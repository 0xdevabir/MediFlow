package com.mediflow.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.audit.chain.AuditRow;
import com.mediflow.audit.chain.CanonicalJson;
import com.mediflow.audit.chain.ChainVerification;
import com.mediflow.audit.metrics.AuditChainMetrics;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifies the audit hash chain (scheduled and on demand) and stores each result. */
@Service
public class AuditChainService {

  private static final Logger log = LoggerFactory.getLogger(AuditChainService.class);

  public record ChainCheck(
      UUID id,
      Instant checkedAt,
      boolean ok,
      long rowsChecked,
      long brokenCount,
      Long firstBrokenSeq,
      String brokenSeqs,
      Long headSeq,
      String message) {}

  private final JdbcTemplate jdbc;
  private final JdbcTemplate streamingJdbc;
  private final TransactionTemplate readTx;
  private final CanonicalJson canonicalJson;
  private final AuditPort audit;
  private final AuditChainMetrics metrics;
  private final ClockPort clock;

  public AuditChainService(
      JdbcTemplate jdbc,
      PlatformTransactionManager txManager,
      ObjectMapper mapper,
      AuditPort audit,
      AuditChainMetrics metrics,
      ClockPort clock) {
    this.jdbc = jdbc;
    // own template so the fetch size does not leak into the shared JdbcTemplate bean
    this.streamingJdbc = new JdbcTemplate(jdbc.getDataSource());
    this.streamingJdbc.setFetchSize(500);
    this.audit = audit;
    this.metrics = metrics;
    this.clock = clock;
    this.canonicalJson = new CanonicalJson(mapper);
    this.readTx = new TransactionTemplate(txManager);
    this.readTx.setReadOnly(true);
  }

  @Scheduled(cron = "${mediflow.audit.verify-cron:0 15 * * * *}")
  public void scheduledVerify() {
    ChainCheck c = verifyNow(null);
    if (!c.ok()) {
      log.error("AUDIT CHAIN BROKEN: {}", c.message());
    }
  }

  @PreAuthorize("hasRole('ADMIN')")
  public ChainCheck verifyAsAdmin(UUID adminUserId) {
    ChainCheck c = verifyNow(adminUserId);
    audit.record(
        adminUserId,
        null,
        "AUDIT_CHAIN_VERIFIED",
        "AuditChainCheck",
        c.id(),
        Map.of("ok", c.ok(), "rowsChecked", c.rowsChecked(), "brokenCount", c.brokenCount()));
    return c;
  }

  /** Full walk in seq order; not access-controlled (used by the scheduler and tests). */
  public ChainCheck verifyNow(UUID triggeredBy) {
    Optional<ChainCheck> previous = lastCheck();
    ChainVerification.Result r =
        readTx.execute(
            status -> {
              ChainVerification v = new ChainVerification();

              streamingJdbc.query(
                  """
                  SELECT seq, id, occurred_at, actor_user_id, patient_id, action, resource_type,
                         resource_id, details::text AS details, prev_hash, hash
                  FROM audit_event ORDER BY seq
                  """,
                  rs -> {
                    v.accept(mapRow(rs));
                  });
              return v.result();
            });

    StringBuilder msg = new StringBuilder();
    boolean ok = r.ok();
    if (!r.ok()) {
      msg.append("Chain broken at #").append(r.firstBrokenSeq()).append(" (").append(r.brokenCount())
          .append(" bad row(s))");
    }
    // Tail truncation cannot be seen from the chain itself: the head recorded by the previous
    // check must still be there with the same hash.
    String headProblem = previous.flatMap(this::headProblem).orElse(null);
    if (headProblem != null) {
      ok = false;
      msg.append(msg.isEmpty() ? "" : "; ").append(headProblem);
    }
    if (ok) {
      msg.append("Chain integrity OK (").append(r.rowsChecked()).append(" rows)");
    }

    UUID id = UUID.randomUUID();
    String broken = r.brokenSeqs().stream().map(String::valueOf).collect(Collectors.joining(","));
    jdbc.update(
        """
        INSERT INTO audit_chain_check (id, ok, rows_checked, broken_count, first_broken_seq,
          broken_seqs, head_seq, head_hash, message, triggered_by)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id, ok, r.rowsChecked(), r.brokenCount(), r.firstBrokenSeq(), broken.isEmpty() ? null : broken,
        r.headSeq(), r.headHash(), msg.toString(), triggeredBy);
    metrics.recordVerification(ok, clock.now().getEpochSecond());
    return lastCheck().filter(c -> c.id().equals(id)).orElseThrow();
  }

  private Optional<String> headProblem(ChainCheck previous) {
    List<Map<String, Object>> prev =
        jdbc.queryForList(
            "SELECT head_seq, head_hash FROM audit_chain_check WHERE id = ?", previous.id());
    if (prev.isEmpty() || prev.get(0).get("head_seq") == null) {
      return Optional.empty();
    }
    long seq = ((Number) prev.get(0).get("head_seq")).longValue();
    String hash = (String) prev.get(0).get("head_hash");
    List<String> now =
        jdbc.queryForList("SELECT hash FROM audit_event WHERE seq = ?", String.class, seq);
    if (now.isEmpty()) {
      return Optional.of("previously verified head #" + seq + " is missing (rows removed)");
    }
    if (!now.get(0).equals(hash)) {
      return Optional.of("previously verified head #" + seq + " has changed");
    }
    return Optional.empty();
  }

  public Optional<ChainCheck> lastCheck() {
    return recentChecks(1).stream().findFirst();
  }

  public List<ChainCheck> recentChecks(int limit) {
    return jdbc.query(
        """
        SELECT id, checked_at, ok, rows_checked, broken_count, first_broken_seq, broken_seqs, head_seq, message
        FROM audit_chain_check ORDER BY checked_at DESC, created_at DESC LIMIT ?
        """,
        (rs, i) ->
            new ChainCheck(
                rs.getObject("id", UUID.class),
                rs.getTimestamp("checked_at").toInstant(),
                rs.getBoolean("ok"),
                rs.getLong("rows_checked"),
                rs.getLong("broken_count"),
                (Long) rs.getObject("first_broken_seq", Long.class),
                rs.getString("broken_seqs"),
                (Long) rs.getObject("head_seq", Long.class),
                rs.getString("message")),
        limit);
  }

  private AuditRow mapRow(ResultSet rs) throws SQLException {
    return new AuditRow(
        rs.getLong("seq"),
        rs.getObject("id", UUID.class),
        rs.getTimestamp("occurred_at").toInstant(),
        rs.getObject("actor_user_id", UUID.class),
        rs.getObject("patient_id", UUID.class),
        rs.getString("action"),
        rs.getString("resource_type"),
        rs.getObject("resource_id", UUID.class),
        canonicalDetails(rs.getString("details")),
        rs.getString("prev_hash"),
        rs.getString("hash"));
  }

  private String canonicalDetails(String raw) {
    try {
      return canonicalJson.ofText(raw);
    } catch (IllegalArgumentException e) {
      return raw; // cannot be what the writer hashed -> row is reported as broken
    }
  }
}
