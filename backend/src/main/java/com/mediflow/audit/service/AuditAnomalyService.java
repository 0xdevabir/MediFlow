package com.mediflow.audit.service;

import com.mediflow.admin.settings.SystemSettings;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detects suspicious audit patterns (e.g. one user reading too many distinct patients in an hour)
 * and lets admins sign off the alerts.
 */
@Service
public class AuditAnomalyService {

  public static final String KIND_PATIENTS_PER_HOUR = "PATIENTS_PER_HOUR";

  private static final Logger log = LoggerFactory.getLogger(AuditAnomalyService.class);

  public record AnomalyRow(
      UUID id,
      UUID actorUserId,
      String actorName,
      String kind,
      Instant windowStart,
      Instant windowEnd,
      int observedCount,
      int threshold,
      UUID reviewedBy,
      Instant reviewedAt,
      String reviewNote) {

    public boolean reviewed() {
      return reviewedAt != null;
    }
  }

  private final JdbcTemplate jdbc;
  private final SystemSettings settings;
  private final ClockPort clock;
  private final AuditPort audit;

  public AuditAnomalyService(
      JdbcTemplate jdbc, SystemSettings settings, ClockPort clock, AuditPort audit) {
    this.jdbc = jdbc;
    this.settings = settings;
    this.clock = clock;
    this.audit = audit;
  }

  @Scheduled(cron = "${mediflow.audit.anomaly-cron:0 30 * * * *}")
  public void scheduledScan() {
    int created = scanPatientsPerHour();
    if (created > 0) {
      log.warn("Audit anomaly scan created {} alert(s)", created);
    }
  }

  /** Scans the previous full hour for users exceeding the configured patient-touch threshold. */
  @Transactional
  public int scanPatientsPerHour() {
    int threshold = settings.currentInt("audit.anomaly.patients-per-hour", 50);
    Instant end = clock.now().truncatedTo(ChronoUnit.HOURS);
    Instant start = end.minus(1, ChronoUnit.HOURS);
    List<Map<String, Object>> hits =
        jdbc.queryForList(
            """
            SELECT actor_user_id, COUNT(DISTINCT patient_id) AS n
            FROM audit_event
            WHERE occurred_at >= ? AND occurred_at < ?
              AND actor_user_id IS NOT NULL
              AND patient_id IS NOT NULL
            GROUP BY actor_user_id
            HAVING COUNT(DISTINCT patient_id) > ?
            """,
            Timestamp.from(start),
            Timestamp.from(end),
            threshold);
    int created = 0;
    for (Map<String, Object> hit : hits) {
      UUID actor = (UUID) hit.get("actor_user_id");
      int n = ((Number) hit.get("n")).intValue();
      int updated =
          jdbc.update(
              """
              INSERT INTO audit_anomaly
                (actor_user_id, kind, window_start, window_end, observed_count, threshold)
              VALUES (?, ?, ?, ?, ?, ?)
              ON CONFLICT (actor_user_id, kind, window_start) DO NOTHING
              """,
              actor,
              KIND_PATIENTS_PER_HOUR,
              Timestamp.from(start),
              Timestamp.from(end),
              n,
              threshold);
      created += updated;
    }
    return created;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public List<AnomalyRow> list(boolean openOnly) {
    String sql =
        """
        SELECT a.id, a.actor_user_id, u.display_name, a.kind, a.window_start, a.window_end,
               a.observed_count, a.threshold, a.reviewed_by, a.reviewed_at, a.review_note
        FROM audit_anomaly a
        LEFT JOIN app_user u ON u.id = a.actor_user_id
        """
            + (openOnly ? " WHERE a.reviewed_at IS NULL" : "")
            + " ORDER BY a.window_start DESC LIMIT 200";
    return jdbc.query(
        sql,
        (rs, i) ->
            new AnomalyRow(
                rs.getObject("id", UUID.class),
                rs.getObject("actor_user_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("kind"),
                rs.getTimestamp("window_start").toInstant(),
                rs.getTimestamp("window_end").toInstant(),
                rs.getInt("observed_count"),
                rs.getInt("threshold"),
                rs.getObject("reviewed_by", UUID.class),
                rs.getTimestamp("reviewed_at") == null
                    ? null
                    : rs.getTimestamp("reviewed_at").toInstant(),
                rs.getString("review_note")));
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public void review(UUID anomalyId, UUID adminUserId, String note) {
    int n =
        jdbc.update(
            """
            UPDATE audit_anomaly
            SET reviewed_by = ?, reviewed_at = ?, review_note = ?, updated_at = ?, version = version + 1
            WHERE id = ? AND reviewed_at IS NULL
            """,
            adminUserId,
            Timestamp.from(clock.now()),
            note,
            Timestamp.from(clock.now()),
            anomalyId);
    if (n == 0) {
      throw new IllegalArgumentException("audit.anomaly.notFound");
    }
    audit.record(
        adminUserId,
        null,
        "AUDIT_ANOMALY_REVIEWED",
        "AuditAnomaly",
        anomalyId,
        Map.of("note", note == null ? "" : note));
  }
}
