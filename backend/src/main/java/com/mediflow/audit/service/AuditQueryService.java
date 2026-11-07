package com.mediflow.audit.service;

import com.mediflow.shared.port.AuditPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-side queries over {@code audit_event} for patients and admins. */
@Service
public class AuditQueryService {

  public record AccessRow(
      Instant occurredAt,
      String actorName,
      String actorRoles,
      String action,
      String purpose,
      String resourceType,
      UUID resourceId) {}

  public record SearchRow(
      long seq,
      Instant occurredAt,
      UUID actorUserId,
      String actorName,
      UUID patientId,
      String action,
      String resourceType,
      UUID resourceId,
      String purpose,
      String outcome) {}

  public record SearchFilter(
      UUID actorUserId, UUID patientId, String action, Instant from, Instant to, int limit) {}

  private final JdbcTemplate jdbc;
  private final AuditPort audit;

  public AuditQueryService(JdbcTemplate jdbc, AuditPort audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  /** Patient-facing "who accessed my record". Never returns clinical content from details. */
  @PreAuthorize("hasRole('PATIENT')")
  @Transactional(readOnly = true)
  public List<AccessRow> forPatient(UUID patientId, UUID viewerUserId) {
    List<AccessRow> rows =
        jdbc.query(
            """
            SELECT e.occurred_at, u.display_name, e.action, e.resource_type, e.resource_id,
                   e.details ->> 'purpose' AS purpose,
                   (SELECT string_agg(ur.role, ', ' ORDER BY ur.role)
                      FROM user_role ur WHERE ur.user_id = e.actor_user_id) AS roles
            FROM audit_event e
            LEFT JOIN app_user u ON u.id = e.actor_user_id
            WHERE e.patient_id = ?
            ORDER BY e.occurred_at DESC
            LIMIT 500
            """,
            (rs, i) -> mapAccess(rs),
            patientId);
    audit.record(
        viewerUserId,
        patientId,
        "AUDIT_PATIENT_VIEW",
        "AuditEvent",
        null,
        Map.of("count", rows.size()));
    return rows;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public List<SearchRow> search(SearchFilter filter, UUID adminUserId) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT e.seq, e.occurred_at, e.actor_user_id, u.display_name, e.patient_id, e.action,
                   e.resource_type, e.resource_id,
                   e.details ->> 'purpose' AS purpose,
                   e.details ->> 'outcome' AS outcome
            FROM audit_event e
            LEFT JOIN app_user u ON u.id = e.actor_user_id
            WHERE 1=1
            """);
    List<Object> args = new ArrayList<>();
    if (filter.actorUserId() != null) {
      sql.append(" AND e.actor_user_id = ?");
      args.add(filter.actorUserId());
    }
    if (filter.patientId() != null) {
      sql.append(" AND e.patient_id = ?");
      args.add(filter.patientId());
    }
    if (filter.action() != null && !filter.action().isBlank()) {
      sql.append(" AND e.action = ?");
      args.add(filter.action().trim());
    }
    if (filter.from() != null) {
      sql.append(" AND e.occurred_at >= ?");
      args.add(Timestamp.from(filter.from()));
    }
    if (filter.to() != null) {
      sql.append(" AND e.occurred_at <= ?");
      args.add(Timestamp.from(filter.to()));
    }
    sql.append(" ORDER BY e.occurred_at DESC LIMIT ?");
    int limit = filter.limit() <= 0 ? 200 : Math.min(filter.limit(), 1000);
    args.add(limit);

    List<SearchRow> rows = jdbc.query(sql.toString(), (rs, i) -> mapSearch(rs), args.toArray());
    audit.record(
        adminUserId,
        filter.patientId(),
        "AUDIT_SEARCH",
        "AuditEvent",
        null,
        Map.of(
            "count",
            rows.size(),
            "action",
            filter.action() == null ? "" : filter.action(),
            "actor",
            filter.actorUserId() == null ? "" : filter.actorUserId().toString()));
    return rows;
  }

  /** Resolves the patient row linked to an app_user, if any. */
  @Transactional(readOnly = true)
  public java.util.Optional<UUID> patientIdForUser(UUID userId) {
    return jdbc
        .queryForList("SELECT id FROM patient WHERE user_id = ? LIMIT 1", UUID.class, userId)
        .stream()
        .findFirst();
  }

  private static AccessRow mapAccess(ResultSet rs) throws SQLException {
    return new AccessRow(
        rs.getTimestamp("occurred_at").toInstant(),
        rs.getString("display_name"),
        rs.getString("roles"),
        rs.getString("action"),
        rs.getString("purpose"),
        rs.getString("resource_type"),
        rs.getObject("resource_id", UUID.class));
  }

  private static SearchRow mapSearch(ResultSet rs) throws SQLException {
    return new SearchRow(
        rs.getLong("seq"),
        rs.getTimestamp("occurred_at").toInstant(),
        rs.getObject("actor_user_id", UUID.class),
        rs.getString("display_name"),
        rs.getObject("patient_id", UUID.class),
        rs.getString("action"),
        rs.getString("resource_type"),
        rs.getObject("resource_id", UUID.class),
        rs.getString("purpose"),
        rs.getString("outcome"));
  }
}
