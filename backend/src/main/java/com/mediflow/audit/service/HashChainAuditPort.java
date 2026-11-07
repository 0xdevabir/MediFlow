package com.mediflow.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.audit.chain.AuditHasher;
import com.mediflow.audit.chain.AuditRow;
import com.mediflow.audit.chain.CanonicalJson;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real {@link AuditPort} (replaces T00's logging stub). Appends to {@code audit_event} with a hash
 * chain.
 *
 * <p>Ordering: every append runs in its own short transaction ({@code REQUIRES_NEW}) that first
 * takes a PostgreSQL transaction-level advisory lock, so there is exactly one writer at a time
 * across threads and application instances; the lock is released at commit. The audit row commits
 * even if the caller's transaction later rolls back (the attempt is still recorded). If the audit
 * write fails the exception propagates, so an unaudited clinical action cannot silently succeed.
 *
 * <p>The task's {@code outcome} and {@code purpose} live in {@code details} (keys {@value
 * #OUTCOME} / {@value #PURPOSE}); T00's port signature has no separate parameters for them.
 */
@Component
public class HashChainAuditPort implements AuditPort {

  public static final String OUTCOME = "outcome";
  public static final String PURPOSE = "purpose";

  /** Arbitrary constant key for pg_advisory_xact_lock (T11 range 55x). */
  static final long CHAIN_LOCK_KEY = 5_501_550L;

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final ClockPort clock;
  private final CanonicalJson canonicalJson;

  public HashChainAuditPort(
      JdbcTemplate jdbc, PlatformTransactionManager txManager, ClockPort clock, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.canonicalJson = new CanonicalJson(mapper);
    this.tx = new TransactionTemplate(txManager);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public void record(
      UUID actorUserId,
      UUID patientId,
      String action,
      String resourceType,
      UUID resourceId,
      Map<String, Object> details) {
    Objects.requireNonNull(action, "action");
    String canonicalDetails = canonicalJson.ofMap(details);
    tx.executeWithoutResult(
        status ->
            append(
                actorUserId,
                patientId,
                truncate(action, 64),
                truncate(resourceType, 64),
                resourceId,
                canonicalDetails));
  }

  private void append(
      UUID actor, UUID patient, String action, String resourceType, UUID resourceId, String details) {
    jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(?)", Integer.class, CHAIN_LOCK_KEY);
    List<String> last =
        jdbc.queryForList("SELECT hash FROM audit_event ORDER BY seq DESC LIMIT 1", String.class);
    String prev = last.isEmpty() || last.get(0) == null ? AuditHasher.GENESIS : last.get(0);

    UUID id = UUID.randomUUID();
    // PostgreSQL keeps microseconds; truncate so the hashed instant equals the stored one.
    Instant at = clock.now().truncatedTo(ChronoUnit.MICROS);
    AuditRow row =
        new AuditRow(0, id, at, actor, patient, action, resourceType, resourceId, details, prev, null);
    String hash = AuditHasher.hash(prev, row);

    jdbc.update(
        """
        INSERT INTO audit_event
          (id, occurred_at, actor_user_id, patient_id, action, resource_type, resource_id,
           details, prev_hash, hash, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
        """,
        id,
        Timestamp.from(at),
        actor,
        patient,
        action,
        resourceType,
        resourceId,
        details,
        prev,
        hash,
        Timestamp.from(at));
  }

  private static String truncate(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
  }
}
