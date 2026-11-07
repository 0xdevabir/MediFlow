package com.mediflow.pharmacy.dispensing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Resolves the pharmacist's hospital via practitioner_role (active). */
@Component
public class PharmacyHospitalResolver {

  private final NamedParameterJdbcTemplate jdbc;

  public PharmacyHospitalResolver(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<UUID> hospitalForUser(UUID userId) {
    if (userId == null) {
      return Optional.empty();
    }
    List<UUID> ids =
        jdbc.query(
            """
            SELECT pr.hospital_id
            FROM practitioner p
            JOIN practitioner_role pr ON pr.practitioner_id = p.id AND pr.active = TRUE
            WHERE p.user_id = :u
            ORDER BY pr.created_at
            LIMIT 1
            """,
            Map.of("u", userId),
            (rs, n) -> rs.getObject(1, UUID.class));
    return ids.stream().findFirst();
  }

  /** Fallback: first active hospital (dev / admin without a role row). */
  public Optional<UUID> anyActiveHospital() {
    List<UUID> ids =
        jdbc.query(
            "SELECT id FROM hospital WHERE active = TRUE ORDER BY name LIMIT 1",
            (rs, n) -> rs.getObject(1, UUID.class));
    return ids.stream().findFirst();
  }

  public UUID requireHospital(UUID userId) {
    return hospitalForUser(userId)
        .or(() -> anyActiveHospital())
        .orElseThrow(() -> new IllegalStateException("No hospital configured for pharmacy"));
  }
}
