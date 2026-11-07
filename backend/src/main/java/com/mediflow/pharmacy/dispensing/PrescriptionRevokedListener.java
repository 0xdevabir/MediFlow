package com.mediflow.pharmacy.dispensing;

import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Blocks further dispensing when T06 publishes {@link DomainEvents.PrescriptionRevoked}. */
@Component
public class PrescriptionRevokedListener {

  private final NamedParameterJdbcTemplate jdbc;
  private final AuditPort audit;
  private final ClockPort clock;
  private final NotificationPort notifications;
  private final ApplicationEventPublisher events;

  public PrescriptionRevokedListener(
      NamedParameterJdbcTemplate jdbc,
      AuditPort audit,
      ClockPort clock,
      NotificationPort notifications,
      ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.audit = audit;
    this.clock = clock;
    this.notifications = notifications;
    this.events = events;
  }

  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.PrescriptionRevoked e) {
    Integer dispensed =
        jdbc.queryForObject(
            "SELECT count(*) FROM dispense WHERE prescription_id = :id",
            Map.of("id", e.prescriptionId()),
            Integer.class);
    boolean already = dispensed != null && dispensed > 0;
    jdbc.update(
        """
        INSERT INTO pharmacy_rx_block
          (prescription_id, patient_id, revoked_at, already_dispensed, created_at, updated_at)
        VALUES (:rx, :pat, :at, :ad, :now, :now)
        ON CONFLICT (prescription_id) DO UPDATE SET
          revoked_at = EXCLUDED.revoked_at,
          already_dispensed = pharmacy_rx_block.already_dispensed OR EXCLUDED.already_dispensed,
          updated_at = EXCLUDED.updated_at
        """,
        new MapSqlParameterSource()
            .addValue("rx", e.prescriptionId())
            .addValue("pat", e.patientId())
            .addValue("at", java.sql.Timestamp.from(e.at() == null ? clock.now() : e.at()))
            .addValue("ad", already)
            .addValue("now", java.sql.Timestamp.from(clock.now())));

    audit.record(
        null,
        e.patientId(),
        "PHARMACY_RX_BLOCKED",
        "pharmacy_rx_block",
        e.prescriptionId(),
        Map.of("alreadyDispensed", already));

    if (already) {
      events.publishEvent(
          new PharmacyEvents.RevokedRxAlreadyDispensedAlert(
              e.prescriptionId(), e.patientId(), e.at() == null ? clock.now() : e.at()));
      notifyPharmacists(
          e.prescriptionId(),
          "Revoked prescription already dispensed",
          "Prescription "
              + e.prescriptionId()
              + " was revoked but medication was already handed out. Review urgently.");
    }
  }

  private void notifyPharmacists(UUID prescriptionId, String subject, String body) {
    List<UUID> pharmacists =
        jdbc.query(
            """
            SELECT DISTINCT u.id
            FROM app_user u
            JOIN user_role ur ON ur.user_id = u.id
            WHERE ur.role = 'ROLE_PHARMACIST' AND u.enabled = TRUE
            """,
            (rs, n) -> rs.getObject(1, UUID.class));
    for (UUID id : pharmacists) {
      notifications.notifyUser(id, "IN_APP", "en", subject, body);
    }
  }
}
