package com.mediflow.clinical.consultation.service;

import com.mediflow.clinical.consultation.domain.LabBadgeEntity;
import com.mediflow.clinical.consultation.repo.ClinicalEncounterRepository;
import com.mediflow.clinical.consultation.repo.ClinicalPractitionerRepository;
import com.mediflow.clinical.consultation.repo.LabBadgeRepository;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.EncounterStatus;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Listens for {@link DomainEvents.LabReportVerified} and raises a doctor notification badge. */
@Component
public class LabReportBadgeListener {

  private static final Logger log = LoggerFactory.getLogger(LabReportBadgeListener.class);

  private final ClinicalEncounterRepository encounters;
  private final ClinicalPractitionerRepository practitioners;
  private final LabBadgeRepository badges;
  private final NotificationPort notifications;
  private final ClockPort clock;

  public LabReportBadgeListener(
      ClinicalEncounterRepository encounters,
      ClinicalPractitionerRepository practitioners,
      LabBadgeRepository badges,
      NotificationPort notifications,
      ClockPort clock) {
    this.encounters = encounters;
    this.practitioners = practitioners;
    this.badges = badges;
    this.notifications = notifications;
    this.clock = clock;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onLabReportVerified(DomainEvents.LabReportVerified event) {
    List<Encounter> open =
        encounters.findByPatientIdAndStatus(event.patientId(), EncounterStatus.IN_PROGRESS);
    Set<UUID> doctorUserIds = new HashSet<>();
    for (Encounter e : open) {
      if (e.getDoctorId() == null) {
        continue;
      }
      practitioners
          .findById(e.getDoctorId())
          .map(Practitioner::getUserId)
          .ifPresent(doctorUserIds::add);
    }
    if (doctorUserIds.isEmpty()) {
      log.debug("No open-encounter doctor for lab badge patient={}", event.patientId());
      return;
    }
    for (UUID doctorUserId : doctorUserIds) {
      if (badges.findByDoctorUserIdAndReportId(doctorUserId, event.reportId()).isPresent()) {
        continue;
      }
      LabBadgeEntity badge = new LabBadgeEntity();
      badge.setDoctorUserId(doctorUserId);
      badge.setPatientId(event.patientId());
      badge.setReportId(event.reportId());
      badge.setLabOrderId(event.labOrderId());
      badge.setAbnormal(event.abnormal());
      badge.setCritical(event.critical());
      badges.save(badge);
      String severity = event.critical() ? "CRITICAL" : event.abnormal() ? "ABNORMAL" : "OK";
      notifications.notifyUser(
          doctorUserId,
          "IN_APP",
          "en",
          "Lab report verified (" + severity + ")",
          "A lab report for your patient is ready. reportId=" + event.reportId());
    }
  }

  public long unreadCount(UUID doctorUserId) {
    return badges.countByDoctorUserIdAndReadAtIsNull(doctorUserId);
  }

  public void markReadForPatient(UUID doctorUserId, UUID patientId) {
    List<LabBadgeEntity> unread =
        badges.findByDoctorUserIdAndPatientIdAndReadAtIsNull(doctorUserId, patientId);
    for (LabBadgeEntity b : unread) {
      b.setReadAt(clock.now());
    }
    badges.saveAll(unread);
  }
}
