package com.mediflow.lab.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Which hospitals a staff user works at (practitioner → active practitioner_role). */
@Repository
public class LabStaffQueries {

  private final NamedParameterJdbcTemplate jdbc;

  public LabStaffQueries(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<UUID> hospitalIdsOf(UUID userId) {
    return jdbc.queryForList(
        """
        SELECT DISTINCT pr.hospital_id FROM practitioner p
        JOIN practitioner_role pr ON pr.practitioner_id = p.id
        WHERE p.user_id = :uid AND pr.active
        """,
        new MapSqlParameterSource("uid", userId),
        UUID.class);
  }

  /** Practitioner ids linked to a user (encounter.doctor_id may hold either id). */
  public List<UUID> practitionerIdsOf(UUID userId) {
    return jdbc.queryForList(
        "SELECT id FROM practitioner WHERE user_id = :uid",
        new MapSqlParameterSource("uid", userId),
        UUID.class);
  }
}
