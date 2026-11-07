package com.mediflow.prescription.sign;

import com.mediflow.prescription.domain.RxIncident;
import com.mediflow.prescription.domain.RxPrescriptionMeta;
import com.mediflow.prescription.repo.RxClinicalQueries;
import com.mediflow.prescription.repo.RxIncidentRepository;
import com.mediflow.prescription.repo.RxPrescriptionMetaRepository;
import com.mediflow.prescription.repo.RxPrescriptionQueries;
import com.mediflow.prescription.support.RxAccess;
import com.mediflow.prescription.support.RxExceptions.ValidationException;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionStatus;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeService {

  private final RxAccess access;
  private final RxPrescriptionQueries prescriptions;
  private final RxPrescriptionMetaRepository metas;
  private final RxIncidentRepository incidents;
  private final RxClinicalQueries clinical;
  private final NotificationPort notifications;
  private final ApplicationEventPublisher events;
  private final AuditPort audit;
  private final ClockPort clock;

  public RevokeService(
      RxAccess access,
      RxPrescriptionQueries prescriptions,
      RxPrescriptionMetaRepository metas,
      RxIncidentRepository incidents,
      RxClinicalQueries clinical,
      NotificationPort notifications,
      ApplicationEventPublisher events,
      AuditPort audit,
      ClockPort clock) {
    this.access = access;
    this.prescriptions = prescriptions;
    this.metas = metas;
    this.incidents = incidents;
    this.clinical = clinical;
    this.notifications = notifications;
    this.events = events;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  @PreAuthorize("hasRole('DOCTOR')")
  public Prescription revoke(UUID prescriptionId, String reason) {
    if (reason == null || reason.trim().length() < 5) {
      throw new ValidationException("rx.error.revoke_reason", "Reason required");
    }
    Prescription rx =
        prescriptions
            .findById(prescriptionId)
            .orElseThrow(() -> new ValidationException("rx.error.not_found", "not found"));
    if (rx.getStatus() != PrescriptionStatus.SIGNED && rx.getStatus() != PrescriptionStatus.AMENDED) {
      throw new ValidationException("rx.error.not_signed", "Only signed/amended Rx can be revoked");
    }
    UUID doctor = access.requireDoctorUserId();
    access.requireCanPrescribe(rx.getPatientId(), rx.getEncounterId());

    boolean dispensed = clinical.dispenseCount(prescriptionId) > 0;

    rx.setStatus(PrescriptionStatus.ENTERED_IN_ERROR);
    rx.setRevokedAt(clock.now());
    prescriptions.save(rx);

    RxPrescriptionMeta meta =
        metas
            .findByPrescriptionId(prescriptionId)
            .orElseGet(
                () -> {
                  RxPrescriptionMeta m = new RxPrescriptionMeta();
                  m.setPrescriptionId(prescriptionId);
                  m.setDoctorUserId(doctor);
                  m.setInputMode(com.mediflow.prescription.domain.RxInputMode.MANUAL);
                  return m;
                });
    meta.setRevokeReason(reason.trim());
    meta.setRevokedBy(doctor);
    metas.save(meta);

    RxIncident incident = new RxIncident();
    incident.setPrescriptionId(prescriptionId);
    incident.setOpenedBy(doctor);
    incident.setOpenedAt(clock.now());
    incident.setReviewDueAt(clock.now().plus(24, ChronoUnit.HOURS));
    incident.setDispensedBeforeRevoke(dispensed);
    incident.setReason(reason.trim());
    incident.setStatus("OPEN");
    incidents.save(incident);

    events.publishEvent(
        new DomainEvents.PrescriptionRevoked(rx.getId(), rx.getPatientId(), clock.now()));

    // Notify doctor / pharmacy / patient (T10 also listens to the event)
    notifications.notifyUser(
        doctor, "IN_APP", "en", "Prescription revoked", "Rx " + prescriptionId + " entered in error");
    for (AppUser pharm : clinical.usersWithRole(Role.ROLE_PHARMACIST)) {
      notifications.notifyUser(
          pharm.getId(),
          "IN_APP",
          "en",
          "Prescription revoked",
          "Rx " + prescriptionId + " entered in error");
    }
    notifications.notifyPatient(
        rx.getPatientId(),
        "IN_APP",
        "en",
        "Prescription cancelled",
        "A prescription was cancelled. Contact your clinic.");
    if (dispensed) {
      notifications.notifyUser(
          doctor,
          "IN_APP",
          "en",
          "Dispensed Rx revoked — contact patient",
          "Medicine may already have been dispensed for " + prescriptionId);
    }

    audit.record(
        doctor,
        rx.getPatientId(),
        "RX_REVOKE",
        "Prescription",
        rx.getId(),
        Map.of("dispensed", dispensed, "incident", incident.getId().toString()));
    return rx;
  }

  @Transactional(readOnly = true)
  public List<RxIncident> openIncidents() {
    return incidents.findByStatusOrderByReviewDueAtAsc("OPEN");
  }
}

