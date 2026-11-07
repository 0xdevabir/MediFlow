package com.mediflow.audit.service;

import com.mediflow.admin.settings.SystemSettings;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compliance CSV export. Retention years come from {@code audit.retention.years}; events older than
 * that are still exported (archive), never deleted from the append-only table.
 */
@Service
public class AuditExportService {

  public record ExportResult(byte[] csv, Instant fromInclusive, Instant toExclusive, int rows) {}

  private final JdbcTemplate jdbc;
  private final SystemSettings settings;
  private final ClockPort clock;
  private final AuditPort audit;

  public AuditExportService(
      JdbcTemplate jdbc, SystemSettings settings, ClockPort clock, AuditPort audit) {
    this.jdbc = jdbc;
    this.settings = settings;
    this.clock = clock;
    this.audit = audit;
  }

  public int retentionYears() {
    return Math.max(1, settings.currentInt("audit.retention.years", 10));
  }

  /** Export events in {@code [from, to)} (defaults: retention window ending now). */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public ExportResult exportCsv(Instant from, Instant to, UUID adminUserId) {
    Instant end = to != null ? to : clock.now();
    Instant start =
        from != null ? from : end.minus(retentionYears() * 365L, ChronoUnit.DAYS);

    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT seq, occurred_at, actor_user_id, patient_id, action, resource_type, resource_id,
                   details ->> 'purpose' AS purpose,
                   details ->> 'outcome' AS outcome,
                   hash
            FROM audit_event
            WHERE occurred_at >= ? AND occurred_at < ?
            ORDER BY seq
            """,
            Timestamp.from(start),
            Timestamp.from(end));

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (PrintWriter out = new PrintWriter(new OutputStreamWriter(bos, StandardCharsets.UTF_8))) {
      out.println("seq,occurred_at,actor_user_id,patient_id,action,resource_type,resource_id,purpose,outcome,hash");
      for (Map<String, Object> r : rows) {
        out.printf(
            "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
            csv(r.get("seq")),
            csv(r.get("occurred_at")),
            csv(r.get("actor_user_id")),
            csv(r.get("patient_id")),
            csv(r.get("action")),
            csv(r.get("resource_type")),
            csv(r.get("resource_id")),
            csv(r.get("purpose")),
            csv(r.get("outcome")),
            csv(r.get("hash")));
      }
    }

    audit.record(
        adminUserId,
        null,
        "AUDIT_EXPORT",
        "AuditEvent",
        null,
        Map.of("rows", rows.size(), "from", start.toString(), "to", end.toString()));
    return new ExportResult(bos.toByteArray(), start, end, rows.size());
  }

  private static String csv(Object v) {
    if (v == null) {
      return "";
    }
    String s = v.toString();
    if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) {
      return '"' + s.replace("\"", "\"\"") + '"';
    }
    return s;
  }
}
