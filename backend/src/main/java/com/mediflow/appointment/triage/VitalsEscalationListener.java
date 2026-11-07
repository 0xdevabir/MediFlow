package com.mediflow.appointment.triage;

import com.mediflow.appointment.queue.ApptEncounterRepository;
import com.mediflow.appointment.queue.QueueService;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.VitalClass;
import com.mediflow.shared.event.DomainEvents;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Auto-escalation: when T05 publishes {@link DomainEvents.VitalsClassified} with patient-level
 * CRITICAL, raise a Red Alert even if the nurse chose a lower triage level.
 */
@Component
public class VitalsEscalationListener {

  private static final Logger log = LoggerFactory.getLogger(VitalsEscalationListener.class);

  private final ApptEncounterRepository encounters;
  private final QueueService queueService;
  private final TriageService triageService;

  public VitalsEscalationListener(
      ApptEncounterRepository encounters,
      QueueService queueService,
      TriageService triageService) {
    this.encounters = encounters;
    this.queueService = queueService;
    this.triageService = triageService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onVitalsClassified(DomainEvents.VitalsClassified event) {
    if (event == null || event.patientLevel() == null) {
      return;
    }
    UUID appointmentId = resolveAppointmentId(event.encounterId());
    if (appointmentId == null) {
      log.debug(
          "VitalsClassified for encounter {} with no appointment — ignored", event.encounterId());
      return;
    }
    // Keep queue ordering aware of borderline/critical vitals for URGENT ranking.
    if (event.patientLevel() == VitalClass.BORDERLINE
        || event.patientLevel() == VitalClass.CRITICAL) {
      queueService.applyTriageLevel(appointmentId, null, event.patientLevel());
    }
    if (event.patientLevel() == VitalClass.CRITICAL) {
      triageService.escalateFromVitals(
          appointmentId, "Auto-escalation: vitals classified CRITICAL");
    }
  }

  private UUID resolveAppointmentId(UUID encounterId) {
    if (encounterId == null) {
      return null;
    }
    return encounters.findById(encounterId).map(Encounter::getAppointmentId).orElse(null);
  }
}
