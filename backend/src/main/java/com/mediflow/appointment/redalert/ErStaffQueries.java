package com.mediflow.appointment.redalert;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Finds on-duty (hospital-linked) ER staff and picks the least-loaded assignee so Red Alerts never
 * leave {@code assigned_er_user_id} null when any {@code ROLE_ER} user exists.
 */
@Repository
public class ErStaffQueries {

  private final NamedParameterJdbcTemplate jdbc;

  public ErStaffQueries(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Prefer ER users with an active practitioner_role at {@code hospitalId}; if none, all enabled ER
   * users. Ordered by fewest open Red Alert assignments, then user id (stable round-robin proxy).
   */
  public List<UUID> erCandidatesForHospital(UUID hospitalId) {
    if (hospitalId != null) {
      List<UUID> onDuty =
          jdbc.queryForList(
              """
              SELECT u.id
              FROM app_user u
              JOIN user_role ur ON ur.user_id = u.id AND ur.role = 'ROLE_ER'
              JOIN practitioner p ON p.user_id = u.id
              JOIN practitioner_role pr ON pr.practitioner_id = p.id
              WHERE u.enabled = TRUE
                AND pr.active = TRUE
                AND pr.hospital_id = :hid
              GROUP BY u.id
              ORDER BY (
                  SELECT COUNT(*) FROM red_alert ra
                  WHERE ra.assigned_er_user_id = u.id AND ra.handover_status IS NULL
                ) ASC,
                u.id ASC
              """,
              new MapSqlParameterSource("hid", hospitalId),
              UUID.class);
      if (!onDuty.isEmpty()) {
        return onDuty;
      }
    }
    return jdbc.queryForList(
        """
        SELECT u.id
        FROM app_user u
        JOIN user_role ur ON ur.user_id = u.id AND ur.role = 'ROLE_ER'
        WHERE u.enabled = TRUE
        GROUP BY u.id
        ORDER BY (
            SELECT COUNT(*) FROM red_alert ra
            WHERE ra.assigned_er_user_id = u.id AND ra.handover_status IS NULL
          ) ASC,
          u.id ASC
        """,
        new MapSqlParameterSource(),
        UUID.class);
  }

  public String displayName(UUID userId) {
    if (userId == null) {
      return null;
    }
    List<String> names =
        jdbc.queryForList(
            "SELECT display_name FROM app_user WHERE id = :id",
            new MapSqlParameterSource("id", userId),
            String.class);
    return names.isEmpty() ? null : names.get(0);
  }
}
