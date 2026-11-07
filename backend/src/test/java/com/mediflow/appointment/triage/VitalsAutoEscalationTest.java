package com.mediflow.appointment.triage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.appointment.queue.ApptEncounterRepository;
import com.mediflow.appointment.queue.QueueService;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.VitalClass;
import com.mediflow.shared.event.DomainEvents;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VitalsAutoEscalationTest {

  @Mock ApptEncounterRepository encounters;
  @Mock QueueService queueService;
  @Mock TriageService triageService;

  VitalsEscalationListener listener;
  UUID encounterId = UUID.randomUUID();
  UUID appointmentId = UUID.randomUUID();
  UUID patientId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    listener = new VitalsEscalationListener(encounters, queueService, triageService);
    Encounter enc = new Encounter();
    enc.setId(encounterId);
    enc.setAppointmentId(appointmentId);
    enc.setPatientId(patientId);
    when(encounters.findById(encounterId)).thenReturn(Optional.of(enc));
  }

  @Test
  void criticalVitalsRaiseRedAlertViaTriage() {
    listener.onVitalsClassified(
        new DomainEvents.VitalsClassified(
            encounterId, patientId, VitalClass.CRITICAL, Instant.now()));

    verify(queueService).applyTriageLevel(appointmentId, null, VitalClass.CRITICAL);
    verify(triageService)
        .escalateFromVitals(eq(appointmentId), any());
  }

  @Test
  void borderlineOnlyUpdatesQueueVitals() {
    listener.onVitalsClassified(
        new DomainEvents.VitalsClassified(
            encounterId, patientId, VitalClass.BORDERLINE, Instant.now()));

    verify(queueService).applyTriageLevel(appointmentId, null, VitalClass.BORDERLINE);
    verify(triageService, never()).escalateFromVitals(any(), any());
  }

  @Test
  void normalVitalsDoNothing() {
    listener.onVitalsClassified(
        new DomainEvents.VitalsClassified(
            encounterId, patientId, VitalClass.NORMAL, Instant.now()));

    verify(queueService, never()).applyTriageLevel(any(), any(), any());
    verify(triageService, never()).escalateFromVitals(any(), any());
  }
}
