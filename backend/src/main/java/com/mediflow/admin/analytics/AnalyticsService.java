package com.mediflow.admin.analytics;

import com.mediflow.shared.port.AuditPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregated operational KPIs for admins — no clinical content (no diagnoses, free text, vitals
 * values, or prescription details).
 */
@Service
public class AnalyticsService {

  public record Kpi(String key, String unit, BigDecimal value) {}

  private final JdbcTemplate jdbc;
  private final AuditPort audit;

  public AnalyticsService(JdbcTemplate jdbc, AuditPort audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public Map<String, Kpi> dashboard(UUID adminUserId) {
    Map<String, Kpi> out = new LinkedHashMap<>();
    out.put(
        "apptToConsultMinutes",
        avgMinutes(
            "apptToConsultMinutes",
            """
            SELECT AVG(EXTRACT(EPOCH FROM (e.started_at - a.checked_in_at)) / 60.0)
            FROM encounter e
            JOIN appointment a ON a.id = e.appointment_id
            WHERE a.checked_in_at IS NOT NULL AND e.started_at IS NOT NULL
            """));
    out.put(
        "arrivalToErMinutes",
        avgMinutes(
            "arrivalToErMinutes",
            """
            SELECT AVG(EXTRACT(EPOCH FROM (r.raised_at - a.checked_in_at)) / 60.0)
            FROM red_alert r
            JOIN appointment a ON a.id = r.appointment_id
            WHERE a.checked_in_at IS NOT NULL
            """));
    out.put(
        "arrivalToUrgentTriageMinutes",
        avgMinutes(
            "arrivalToUrgentTriageMinutes",
            """
            SELECT AVG(EXTRACT(EPOCH FROM (t.set_at - a.checked_in_at)) / 60.0)
            FROM triage t
            JOIN appointment a ON a.id = t.appointment_id
            WHERE a.checked_in_at IS NOT NULL AND t.level IN ('URGENT','CRITICAL')
            """));
    out.put(
        "orderToReportDoctorMinutes",
        avgMinutes(
            "orderToReportDoctorMinutes",
            """
            SELECT AVG(EXTRACT(EPOCH FROM (d.verified_at - o.ordered_at)) / 60.0)
            FROM diagnostic_report d
            JOIN lab_order o ON o.id = d.lab_order_id
            WHERE d.verified_at IS NOT NULL AND o.order_type = 'DOCTOR'
            """));
    out.put(
        "orderToReportDirectMinutes",
        avgMinutes(
            "orderToReportDirectMinutes",
            """
            SELECT AVG(EXTRACT(EPOCH FROM (d.verified_at - o.ordered_at)) / 60.0)
            FROM diagnostic_report d
            JOIN lab_order o ON o.id = d.lab_order_id
            WHERE d.verified_at IS NOT NULL AND o.order_type = 'DIRECT'
            """));
    out.put("directThenDoctorVisitShare", ratio(
        "directThenDoctorVisitShare",
        """
        SELECT
          COUNT(*) FILTER (
            WHERE EXISTS (
              SELECT 1 FROM encounter e
              WHERE e.patient_id = o.patient_id
                AND e.started_at > o.ordered_at
                AND e.started_at < o.ordered_at + INTERVAL '14 days'
            )
          )::numeric,
          NULLIF(COUNT(*), 0)::numeric
        FROM lab_order o
        WHERE o.order_type = 'DIRECT'
        """));
    out.put(
        "aiFieldsAcceptedUnchanged",
        countKpi(
            "aiFieldsAcceptedUnchanged",
            """
            SELECT COUNT(*) FROM prescription_item
            WHERE review_status = 'ACCEPTED' OR review_status = 'UNCHANGED'
            """));
    out.put(
        "aiFieldsCorrected",
        countKpi(
            "aiFieldsCorrected",
            """
            SELECT COUNT(*) FROM prescription_item
            WHERE review_status IN ('CORRECTED','EDITED','MODIFIED')
            """));
    out.put(
        "rxNeedingClarification",
        countKpi(
            "rxNeedingClarification",
            """
            SELECT COUNT(*) FROM prescription_item
            WHERE review_status IN ('CLARIFY','NEEDS_CLARIFICATION','PENDING')
              AND (confidence IS NULL OR confidence < 0.85
                   OR conflict_severity IN ('MAJOR','CRITICAL'))
            """));
    out.put("reminderAckRate", reminderAckRate());

    audit.record(
        adminUserId,
        null,
        "ADMIN_ANALYTICS_VIEW",
        "Analytics",
        null,
        Map.of("kpis", out.size()));
    return out;
  }

  private Kpi avgMinutes(String key, String sql) {
    Double v = jdbc.queryForObject(sql, Double.class);
    return new Kpi(key, "minutes", round(v));
  }

  private Kpi countKpi(String key, String sql) {
    Long v = jdbc.queryForObject(sql, Long.class);
    return new Kpi(key, "count", BigDecimal.valueOf(v == null ? 0 : v));
  }

  private Kpi ratio(String key, String sql) {
    return jdbc.query(
        sql,
        rs -> {
          if (!rs.next()) {
            return new Kpi(key, "ratio", null);
          }
          BigDecimal num = rs.getBigDecimal(1);
          BigDecimal den = rs.getBigDecimal(2);
          if (num == null || den == null || den.signum() == 0) {
            return new Kpi(key, "ratio", null);
          }
          return new Kpi(key, "ratio", num.divide(den, 4, RoundingMode.HALF_UP));
        });
  }

  private Kpi reminderAckRate() {
    // adherence_log is owned by T10 (V500). If the table is absent, return null rather than fail.
    try {
      return ratio(
          "reminderAckRate",
          """
          SELECT
            COUNT(*) FILTER (WHERE a.outcome = 'TAKEN')::numeric,
            NULLIF(COUNT(*), 0)::numeric
          FROM adherence_log a
          """);
    } catch (Exception e) {
      return new Kpi("reminderAckRate", "ratio", null);
    }
  }

  private static BigDecimal round(Double v) {
    if (v == null || v.isNaN() || v.isInfinite()) {
      return null;
    }
    return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
  }
}
