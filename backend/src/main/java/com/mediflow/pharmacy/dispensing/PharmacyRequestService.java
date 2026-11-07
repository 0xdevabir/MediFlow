package com.mediflow.pharmacy.dispensing;

import com.mediflow.pharmacy.dictionary.DrugDictionaryPort;
import com.mediflow.pharmacy.dictionary.DrugMatch;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionStatus;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import com.mediflow.shared.repo.PrescriptionRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Send-back, substitution (needs doctor approval), and refer-elsewhere requests. */
@Service
public class PharmacyRequestService {

  private final NamedParameterJdbcTemplate jdbc;
  private final PrescriptionRepository prescriptions;
  private final DrugDictionaryPort dictionary;
  private final PharmacyHospitalResolver hospitals;
  private final AuditPort audit;
  private final ClockPort clock;
  private final NotificationPort notifications;
  private final ApplicationEventPublisher events;

  public PharmacyRequestService(
      NamedParameterJdbcTemplate jdbc,
      PrescriptionRepository prescriptions,
      DrugDictionaryPort dictionary,
      PharmacyHospitalResolver hospitals,
      AuditPort audit,
      ClockPort clock,
      NotificationPort notifications,
      ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.prescriptions = prescriptions;
    this.dictionary = dictionary;
    this.hospitals = hospitals;
    this.audit = audit;
    this.clock = clock;
    this.notifications = notifications;
    this.events = events;
  }

  @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
  @Transactional
  public UUID sendBack(UUID prescriptionId, String reason, UUID pharmacistUserId) {
    requireReason(reason);
    Prescription rx = requireSignedOrViewable(prescriptionId);
    UUID doctorUserId = resolveDoctorUserId(rx);
    UUID id =
        insertRequest(
            "SEND_BACK",
            "OPEN",
            prescriptionId,
            null,
            rx.getPatientId(),
            hospitals.requireHospital(pharmacistUserId),
            null,
            reason.trim(),
            pharmacistUserId,
            doctorUserId);
    events.publishEvent(
        new PharmacyEvents.SendBackRequested(
            id, prescriptionId, rx.getPatientId(), doctorUserId, reason.trim(), clock.now()));
    notifyDoctor(doctorUserId, "Pharmacy send-back", "Prescription returned: " + reason.trim());
    audit.record(
        pharmacistUserId,
        rx.getPatientId(),
        "PHARMACY_SEND_BACK",
        "pharmacy_request",
        id,
        Map.of("prescriptionId", prescriptionId.toString()));
    return id;
  }

  @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
  @Transactional
  public UUID requestSubstitution(
      UUID prescriptionId,
      UUID itemId,
      UUID proposedBrandId,
      String reason,
      UUID pharmacistUserId) {
    requireReason(reason);
    Prescription rx = requireSignedOrViewable(prescriptionId);
    DrugMatch proposed =
        dictionary
            .findBrand(proposedBrandId)
            .orElseThrow(() -> new PharmacyException("pharmacy.error.unknownBrand"));
    UUID itemGeneric =
        jdbc
            .query(
                "SELECT generic_id FROM prescription_item WHERE id = :id AND prescription_id = :rx",
                Map.of("id", itemId, "rx", prescriptionId),
                (rs, n) -> rs.getObject(1, UUID.class))
            .stream()
            .findFirst()
            .orElseThrow(() -> new PharmacyException("pharmacy.error.itemNotFound"));
    if (!proposed.genericId().equals(itemGeneric)) {
      throw new PharmacyException("pharmacy.error.genericMismatch");
    }
    UUID doctorUserId = resolveDoctorUserId(rx);
    UUID id =
        insertRequest(
            "SUBSTITUTION",
            "PENDING",
            prescriptionId,
            itemId,
            rx.getPatientId(),
            hospitals.requireHospital(pharmacistUserId),
            proposedBrandId,
            reason.trim(),
            pharmacistUserId,
            doctorUserId);
    events.publishEvent(
        new PharmacyEvents.SubstitutionRequested(
            id,
            prescriptionId,
            itemId,
            rx.getPatientId(),
            doctorUserId,
            proposedBrandId,
            reason.trim(),
            clock.now()));
    notifyDoctor(
        doctorUserId,
        "Substitution approval needed",
        "Proposed brand " + proposed.brandName() + ": " + reason.trim());
    audit.record(
        pharmacistUserId,
        rx.getPatientId(),
        "PHARMACY_SUBSTITUTION_REQUEST",
        "pharmacy_request",
        id,
        Map.of("proposedBrandId", proposedBrandId.toString()));
    return id;
  }

  @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
  @Transactional
  public UUID referElsewhere(UUID prescriptionId, String reason, UUID pharmacistUserId) {
    requireReason(reason);
    Prescription rx = requireSignedOrViewable(prescriptionId);
    UUID id =
        insertRequest(
            "REFERRED_ELSEWHERE",
            "CLOSED",
            prescriptionId,
            null,
            rx.getPatientId(),
            hospitals.requireHospital(pharmacistUserId),
            null,
            reason.trim(),
            pharmacistUserId,
            resolveDoctorUserId(rx));
    events.publishEvent(
        new PharmacyEvents.ReferredElsewhere(
            id, prescriptionId, rx.getPatientId(), reason.trim(), clock.now()));
    audit.record(
        pharmacistUserId,
        rx.getPatientId(),
        "PHARMACY_REFERRED_ELSEWHERE",
        "pharmacy_request",
        id,
        Map.of());
    return id;
  }

  /** Doctor (or admin) decides a pending substitution — interim UI until T06 wires its own. */
  @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
  @Transactional
  public void decideSubstitution(
      UUID requestId, boolean approve, String note, UUID doctorUserId) {
    List<Map<String, Object>> rows =
        jdbc.query(
            """
            SELECT status, prescription_id, doctor_user_id FROM pharmacy_request
            WHERE id = :id AND kind = 'SUBSTITUTION'
            """,
            Map.of("id", requestId),
            (rs, n) -> {
              Map<String, Object> m = new HashMap<>();
              m.put("status", rs.getString("status"));
              m.put("rx", rs.getObject("prescription_id", UUID.class));
              m.put("doctor", rs.getObject("doctor_user_id", UUID.class));
              return m;
            });
    if (rows.isEmpty()) {
      throw new PharmacyException("pharmacy.error.requestNotFound");
    }
    if (!"PENDING".equals(rows.get(0).get("status"))) {
      throw new PharmacyException("pharmacy.error.requestNotPending");
    }
    String status = approve ? "APPROVED" : "REJECTED";
    jdbc.update(
        """
        UPDATE pharmacy_request SET status = :st, decided_by = :by, decided_at = :now,
          decision_note = :note, updated_at = :now
        WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("st", status)
            .addValue("by", doctorUserId)
            .addValue("now", java.sql.Timestamp.from(clock.now()))
            .addValue("note", note)
            .addValue("id", requestId));
    events.publishEvent(
        new PharmacyEvents.SubstitutionDecided(
            requestId, (UUID) rows.get(0).get("rx"), approve, doctorUserId, clock.now()));
    audit.record(
        doctorUserId,
        null,
        approve ? "PHARMACY_SUBSTITUTION_APPROVED" : "PHARMACY_SUBSTITUTION_REJECTED",
        "pharmacy_request",
        requestId,
        Map.of());
  }

  @PreAuthorize("hasAnyRole('DOCTOR','ADMIN','PHARMACIST')")
  @Transactional(readOnly = true)
  public List<Map<String, Object>> pendingForDoctor(UUID doctorUserId) {
    return jdbc.query(
        """
        SELECT id, kind, status, prescription_id, prescription_item_id, proposed_brand_id,
               reason, created_at
        FROM pharmacy_request
        WHERE doctor_user_id = :d AND status = 'PENDING' AND kind = 'SUBSTITUTION'
        ORDER BY created_at
        """,
        Map.of("d", doctorUserId),
        (rs, n) -> {
          Map<String, Object> m = new HashMap<>();
          m.put("id", rs.getObject("id", UUID.class));
          m.put("kind", rs.getString("kind"));
          m.put("status", rs.getString("status"));
          m.put("prescriptionId", rs.getObject("prescription_id", UUID.class));
          m.put("itemId", rs.getObject("prescription_item_id", UUID.class));
          m.put("proposedBrandId", rs.getObject("proposed_brand_id", UUID.class));
          m.put("reason", rs.getString("reason"));
          m.put("createdAt", rs.getTimestamp("created_at").toInstant());
          return m;
        });
  }

  private UUID insertRequest(
      String kind,
      String status,
      UUID prescriptionId,
      UUID itemId,
      UUID patientId,
      UUID hospitalId,
      UUID proposedBrandId,
      String reason,
      UUID requestedBy,
      UUID doctorUserId) {
    GeneratedKeyHolder kh = new GeneratedKeyHolder();
    jdbc.update(
        """
        INSERT INTO pharmacy_request
          (kind, status, prescription_id, prescription_item_id, patient_id, hospital_id,
           proposed_brand_id, reason, requested_by, doctor_user_id, created_at, updated_at)
        VALUES (:k, :st, :rx, :item, :pat, :h, :brand, :reason, :by, :doc, :now, :now)
        """,
        new MapSqlParameterSource()
            .addValue("k", kind)
            .addValue("st", status)
            .addValue("rx", prescriptionId)
            .addValue("item", itemId)
            .addValue("pat", patientId)
            .addValue("h", hospitalId)
            .addValue("brand", proposedBrandId)
            .addValue("reason", reason)
            .addValue("by", requestedBy)
            .addValue("doc", doctorUserId)
            .addValue("now", java.sql.Timestamp.from(clock.now())),
        kh,
        new String[] {"id"});
    return (UUID) kh.getKeys().get("id");
  }

  private Prescription requireSignedOrViewable(UUID prescriptionId) {
    Prescription rx =
        prescriptions
            .findById(prescriptionId)
            .orElseThrow(() -> new PharmacyException("pharmacy.error.rxNotFound"));
    if (rx.getStatus() != PrescriptionStatus.SIGNED || rx.getRevokedAt() != null) {
      throw new PharmacyException("pharmacy.error.notSigned");
    }
    return rx;
  }

  private UUID resolveDoctorUserId(Prescription rx) {
    List<UUID> ids =
        jdbc.query(
            """
            SELECT COALESCE(p.signed_by, pr.user_id)
            FROM prescription p
            JOIN encounter e ON e.id = p.encounter_id
            LEFT JOIN practitioner pr ON pr.id = e.doctor_id
            WHERE p.id = :id
            """,
            Map.of("id", rx.getId()),
            (rs, n) -> rs.getObject(1, UUID.class));
    return ids.stream().findFirst().orElse(null);
  }

  private void notifyDoctor(UUID doctorUserId, String subject, String body) {
    if (doctorUserId == null) {
      return;
    }
    notifications.notifyUser(doctorUserId, "IN_APP", "en", subject, body);
  }

  private static void requireReason(String reason) {
    if (reason == null || reason.trim().length() < 5) {
      throw new PharmacyException("pharmacy.error.reasonRequired");
    }
  }
}
