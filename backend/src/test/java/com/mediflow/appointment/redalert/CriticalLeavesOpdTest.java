package com.mediflow.appointment.redalert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.appointment.queue.ApptEncounterRepository;
import com.mediflow.appointment.queue.QueueOrdering;
import com.mediflow.appointment.queue.QueueService;
import com.mediflow.appointment.support.ApptAppointmentRepository;
import com.mediflow.appointment.support.ApptPatientRepository;
import com.mediflow.appointment.support.ApptUserRepository;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.QueueStatus;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.domain.VitalClass;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CriticalLeavesOpdTest {

  @Mock ApptRedAlertRepository alerts;
  @Mock ApptAppointmentRepository appointments;
  @Mock ApptEncounterRepository encounters;
  @Mock ApptPatientRepository patients;
  @Mock ApptUserRepository users;
  @Mock ErStaffQueries erStaff;
  @Mock QueueService queueService;
  @Mock CurrentUser currentUser;
  @Mock ApplicationEventPublisher events;
  @Mock AuditPort audit;
  @Mock NotificationPort notifications;
  @Mock ClockPort clock;
  @Mock RedAlertMetrics metrics;

  @Test
  void raiseRemovesFromOpdAndCriticalNeverOrdered() {
    RedAlertService service =
        new RedAlertService(
            alerts,
            appointments,
            encounters,
            patients,
            users,
            erStaff,
            queueService,
            currentUser,
            events,
            audit,
            notifications,
            clock,
            metrics);

    UUID apptId = UUID.randomUUID();
    Appointment appt = new Appointment();
    appt.setId(apptId);
    appt.setPatientId(UUID.randomUUID());
    appt.setHospitalId(UUID.randomUUID());
    appt.setPriorityLevel(TriageLevel.NORMAL);

    when(alerts.findFirstByAppointmentIdAndHandoverStatusIsNull(apptId))
        .thenReturn(Optional.empty());
    when(appointments.findById(apptId)).thenReturn(Optional.of(appt));
    when(clock.now()).thenReturn(Instant.parse("2026-11-07T10:00:00Z"));
    when(currentUser.userId()).thenReturn(Optional.empty());
    when(currentUser.hasRole("ER")).thenReturn(false);
    when(erStaff.erCandidatesForHospital(appt.getHospitalId())).thenReturn(List.of());
    when(users.findEnabledByRole(any())).thenReturn(List.of());
    when(alerts.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              var a = inv.getArgument(0, com.mediflow.shared.domain.RedAlert.class);
              a.setId(UUID.randomUUID());
              return a;
            });
    when(encounters.findByAppointmentId(apptId)).thenReturn(Optional.empty());
    when(patients.findById(appt.getPatientId())).thenReturn(Optional.empty());

    service.raise(apptId, "CRITICAL", "NURSE_TRIAGE");

    verify(queueService).removeFromOpd(eq(apptId), eq("RED_ALERT"));

    var ordered =
        QueueOrdering.order(
            List.of(
                new QueueOrdering.Candidate(
                    UUID.randomUUID(),
                    TriageLevel.CRITICAL,
                    QueueStatus.WAITING,
                    VitalClass.CRITICAL,
                    Instant.now(),
                    null,
                    1,
                    null)));
    assertFalse(
        ordered.stream().anyMatch(c -> c.level() == TriageLevel.CRITICAL),
        "CRITICAL must never appear in OPD waiting list");
  }
}
