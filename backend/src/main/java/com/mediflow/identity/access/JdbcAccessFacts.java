package com.mediflow.identity.access;

import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.PrescriptionStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@link AccessFacts} over the shared schema (read-only, parameterised SQL). */
@Component
@Transactional(readOnly = true)
public class JdbcAccessFacts implements AccessFacts {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcAccessFacts(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<UUID> userIdByUsername(String username) {
    return first(
        "SELECT id FROM app_user WHERE username = :u AND enabled = TRUE",
        new MapSqlParameterSource("u", username));
  }

  @Override
  public Optional<UUID> patientIdOfUser(UUID userId) {
    return first(
        "SELECT id FROM patient WHERE user_id = :u", new MapSqlParameterSource("u", userId));
  }

  @Override
  public Optional<UUID> userIdOfPractitioner(UUID id) {
    return first(
        "SELECT user_id FROM practitioner WHERE id = :id AND user_id IS NOT NULL",
        new MapSqlParameterSource("id", id));
  }

  @Override
  public boolean seenByDoctorToday(UUID doctorUserId, UUID patientId, Instant now) {
    LocalDate today = now.atZone(AccessRules.CLINIC_ZONE).toLocalDate();
    Instant dayStart = today.atStartOfDay(AccessRules.CLINIC_ZONE).toInstant();
    Instant dayEnd = today.plusDays(1).atStartOfDay(AccessRules.CLINIC_ZONE).toInstant();
    MapSqlParameterSource p =
        new MapSqlParameterSource("u", doctorUserId)
            .addValue("p", patientId)
            .addValue("from", Timestamp.from(dayStart))
            .addValue("to", Timestamp.from(dayEnd));
    return exists(
        """
        SELECT 1 FROM appointment a JOIN practitioner pr ON pr.id = a.doctor_id
         WHERE pr.user_id = :u AND a.patient_id = :p
           AND a.status IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED')
           AND a.checked_in_at >= :from AND a.checked_in_at < :to
        UNION ALL
        SELECT 1 FROM encounter e JOIN practitioner pr ON pr.id = e.doctor_id
         WHERE pr.user_id = :u AND e.patient_id = :p
           AND e.status <> 'CANCELLED'
           AND e.started_at >= :from AND e.started_at < :to
        """,
        p);
  }

  @Override
  public boolean activeReferral(UUID userId, UUID patientId, Instant now) {
    return exists(
        """
        SELECT 1 FROM identity_referral_share
         WHERE grantee_user_id = :u AND patient_id = :p AND revoked_at IS NULL
           AND starts_at <= :now AND expires_at > :now
        """,
        new MapSqlParameterSource("u", userId)
            .addValue("p", patientId)
            .addValue("now", Timestamp.from(now)));
  }

  @Override
  public boolean activeBreakGlass(UUID userId, UUID patientId, Instant now) {
    return exists(
        """
        SELECT 1 FROM break_glass_access
         WHERE user_id = :u AND patient_id = :p AND started_at <= :now AND expires_at > :now
        """,
        new MapSqlParameterSource("u", userId)
            .addValue("p", patientId)
            .addValue("now", Timestamp.from(now)));
  }

  @Override
  public boolean activeRedAlert(UUID patientId) {
    return exists(
        "SELECT 1 FROM red_alert WHERE patient_id = :p AND handover_status IS NULL",
        new MapSqlParameterSource("p", patientId));
  }

  @Override
  public boolean openLabOrderForPatient(UUID patientId, Instant now) {
    return exists(
        """
        SELECT 1 FROM lab_order
         WHERE patient_id = :p
           AND (status IN (:open) OR (status = 'VERIFIED' AND updated_at > :since))
        """,
        new MapSqlParameterSource("p", patientId)
            .addValue(
                "open", AccessRules.OPEN_LAB_STATUSES.stream().map(Enum::name).toList())
            .addValue("since", Timestamp.from(now.minus(AccessRules.LAB_VERIFIED_GRACE))));
  }

  @Override
  public boolean signedPrescriptionForPatient(UUID patientId, Instant now) {
    return exists(
        """
        SELECT 1 FROM prescription
         WHERE patient_id = :p AND status IN ('SIGNED', 'AMENDED') AND signed_at > :since
        """,
        new MapSqlParameterSource("p", patientId)
            .addValue(
                "since", Timestamp.from(now.minus(AccessRules.PRESCRIPTION_DISPENSE_WINDOW))));
  }

  @Override
  public boolean userHasRole(UUID userId, com.mediflow.shared.domain.Role role) {
    return exists(
        "SELECT 1 FROM user_role WHERE user_id = :u AND role = :r",
        new MapSqlParameterSource("u", userId).addValue("r", role.name()));
  }

  @Override
  public Optional<LabOrderRef> labOrder(UUID labOrderId) {
    List<LabOrderRef> rows =
        jdbc.query(
            "SELECT id, patient_id, status, updated_at FROM lab_order WHERE id = :id",
            new MapSqlParameterSource("id", labOrderId),
            (rs, i) ->
                new LabOrderRef(
                    rs.getObject("id", UUID.class),
                    rs.getObject("patient_id", UUID.class),
                    LabOrderStatus.valueOf(rs.getString("status")),
                    toInstant(rs.getTimestamp("updated_at"))));
    return rows.stream().findFirst();
  }

  @Override
  public Optional<PrescriptionRef> prescription(UUID prescriptionId) {
    List<PrescriptionRef> rows =
        jdbc.query(
            "SELECT id, patient_id, status, signed_at FROM prescription WHERE id = :id",
            new MapSqlParameterSource("id", prescriptionId),
            (rs, i) ->
                new PrescriptionRef(
                    rs.getObject("id", UUID.class),
                    rs.getObject("patient_id", UUID.class),
                    PrescriptionStatus.valueOf(rs.getString("status")),
                    toInstant(rs.getTimestamp("signed_at"))));
    return rows.stream().findFirst();
  }

  private boolean exists(String sql, MapSqlParameterSource params) {
    Boolean result =
        jdbc.queryForObject("SELECT EXISTS (" + sql + ")", params, Boolean.class);
    return Boolean.TRUE.equals(result);
  }

  private Optional<UUID> first(String sql, MapSqlParameterSource params) {
    return jdbc.queryForList(sql, params, UUID.class).stream().findFirst();
  }

  private static Instant toInstant(Timestamp ts) {
    return ts == null ? null : ts.toInstant();
  }
}
