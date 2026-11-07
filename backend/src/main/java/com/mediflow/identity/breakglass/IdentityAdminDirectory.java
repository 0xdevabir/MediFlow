package com.mediflow.identity.breakglass;

import com.mediflow.shared.domain.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Small read-only lookups for break-glass notifications and the review list. */
@Component
public class IdentityAdminDirectory {

  /** Recipient of an admin notification. */
  public record Recipient(UUID userId, String locale) {}

  private final NamedParameterJdbcTemplate jdbc;

  public IdentityAdminDirectory(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Recipient> enabledUsersWithRole(Role role) {
    return jdbc.query(
        """
        SELECT u.id, u.locale FROM app_user u JOIN user_role r ON r.user_id = u.id
         WHERE r.role = :role AND u.enabled = TRUE
        """,
        new MapSqlParameterSource("role", role.name()),
        (rs, i) -> new Recipient(rs.getObject("id", UUID.class), rs.getString("locale")));
  }

  /** Username per user id (for the review list). */
  public Map<UUID, String> usernames(List<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<UUID, String> out = new java.util.HashMap<>();
    jdbc.query(
        "SELECT id, username FROM app_user WHERE id IN (:ids)",
        new MapSqlParameterSource("ids", ids),
        rs -> {
          out.put(rs.getObject("id", UUID.class), rs.getString("username"));
        });
    return out;
  }

  /** MRN per patient id: the review list shows the MRN only (minimum necessary for admins). */
  public Map<UUID, String> mrns(List<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<UUID, String> out = new java.util.HashMap<>();
    jdbc.query(
        "SELECT id, mrn FROM patient WHERE id IN (:ids)",
        new MapSqlParameterSource("ids", ids),
        rs -> {
          out.put(rs.getObject("id", UUID.class), rs.getString("mrn"));
        });
    return out;
  }

  public boolean patientExists(UUID patientId) {
    Boolean b =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM patient WHERE id = :p)",
            new MapSqlParameterSource("p", patientId),
            Boolean.class);
    return Boolean.TRUE.equals(b);
  }
}
