package com.mediflow.admin.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anonymised aggregated DHIS2 {@code dataValueSets} JSON download. Live push stays disabled until
 * DGHS approval (no outbound HTTP here).
 */
@Service
public class Dhis2ExportService {

  public static final String ORG_UNIT = "MediFlow"; // placeholder until facility mapping exists

  public record Export(byte[] json, String period, int valueCount) {}

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final ClockPort clock;
  private final AuditPort audit;

  public Dhis2ExportService(
      JdbcTemplate jdbc, ObjectMapper mapper, ClockPort clock, AuditPort audit) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
    this.audit = audit;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public Export exportMonth(LocalDate anyDayInMonth, UUID adminUserId) {
    LocalDate month = anyDayInMonth == null
        ? LocalDate.ofInstant(clock.now(), ZoneOffset.UTC).withDayOfMonth(1)
        : anyDayInMonth.withDayOfMonth(1);
    LocalDate next = month.plusMonths(1);
    Instant from = month.atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant to = next.atStartOfDay().toInstant(ZoneOffset.UTC);
    String period = month.format(DateTimeFormatter.ofPattern("yyyyMM"));

    ObjectNode root = mapper.createObjectNode();
    root.put("dataSet", "MediFlow_Aggregates");
    root.put("period", period);
    root.put("orgUnit", ORG_UNIT);
    root.put("completeDate", LocalDate.ofInstant(clock.now(), ZoneOffset.UTC).toString());
    ArrayNode values = root.putArray("dataValues");

    add(values, "VISITS", period, count(
        "SELECT COUNT(*) FROM appointment WHERE booked_at >= ? AND booked_at < ? AND status <> 'CANCELLED'",
        from,
        to));
    add(values, "EMERGENCY_CASES", period, count(
        "SELECT COUNT(*) FROM red_alert WHERE raised_at >= ? AND raised_at < ?", from, to));
    add(values, "PRESCRIPTIONS_ISSUED", period, count(
        "SELECT COUNT(*) FROM prescription WHERE signed_at >= ? AND signed_at < ? AND status = 'SIGNED'",
        from,
        to));
    Double tat =
        jdbc.queryForObject(
            """
            SELECT AVG(EXTRACT(EPOCH FROM (d.verified_at - o.ordered_at)) / 60.0)
            FROM diagnostic_report d
            JOIN lab_order o ON o.id = d.lab_order_id
            WHERE d.verified_at >= ? AND d.verified_at < ?
            """,
            Double.class,
            java.sql.Timestamp.from(from),
            java.sql.Timestamp.from(to));
    add(values, "LAB_TURNAROUND_MINUTES", period, tat == null ? 0L : Math.round(tat));

    byte[] bytes;
    try {
      bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
    } catch (Exception e) {
      throw new IllegalStateException("DHIS2 JSON encode failed", e);
    }

    audit.record(
        adminUserId,
        null,
        "DHIS2_EXPORT",
        "Dhis2Export",
        null,
        Map.of("period", period, "values", values.size(), "push", false));
    return new Export(bytes, period, values.size());
  }

  private long count(String sql, Instant from, Instant to) {
    Long n =
        jdbc.queryForObject(
            sql, Long.class, java.sql.Timestamp.from(from), java.sql.Timestamp.from(to));
    return n == null ? 0L : n;
  }

  private void add(ArrayNode values, String dataElement, String period, long value) {
    ObjectNode v = values.addObject();
    v.put("dataElement", dataElement);
    v.put("period", period);
    v.put("orgUnit", ORG_UNIT);
    v.put("value", Long.toString(value));
  }

  public static String filename(String period) {
    return "dhis2-dataValueSets-" + period + ".json";
  }

  public static MediaHint media() {
    return new MediaHint(
        "application/json", StandardCharsets.UTF_8.name());
  }

  public record MediaHint(String contentType, String charset) {}
}
