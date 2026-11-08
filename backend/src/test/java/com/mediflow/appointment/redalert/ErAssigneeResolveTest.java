package com.mediflow.appointment.redalert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.appointment.queue.ApptEncounterRepository;
import com.mediflow.appointment.queue.QueueService;
import com.mediflow.appointment.support.ApptAppointmentRepository;
import com.mediflow.appointment.support.ApptPatientRepository;
import com.mediflow.appointment.support.ApptUserRepository;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ErAssigneeResolveTest {

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

  RedAlertService service;

  @BeforeEach
  void setUp() {
    service =
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
  }

  @Test
  void resolveAssigneePrefersLoggedInEr() {
    UUID self = UUID.randomUUID();
    when(currentUser.hasRole("ER")).thenReturn(true);
    when(currentUser.userId()).thenReturn(Optional.of(self));

    assertEquals(self, service.resolveAssignee(UUID.randomUUID()));
    verify(erStaff, never()).erCandidatesForHospital(any());
  }

  @Test
  void resolveAssigneeUsesLeastLoadedHospitalEr() {
    UUID hospitalId = UUID.randomUUID();
    UUID erA = UUID.randomUUID();
    UUID erB = UUID.randomUUID();
    when(currentUser.hasRole("ER")).thenReturn(false);
    when(erStaff.erCandidatesForHospital(hospitalId)).thenReturn(List.of(erB, erA));

    assertEquals(erB, service.resolveAssignee(hospitalId));
  }

  @Test
  void resolveAssigneeNullWhenNoErStaff() {
    when(currentUser.hasRole("ER")).thenReturn(false);
    when(erStaff.erCandidatesForHospital(any())).thenReturn(List.of());

    assertNull(service.resolveAssignee(UUID.randomUUID()));
  }

  @Test
  void raisePersistsAssignedErWhenStaffExist() {
    UUID apptId = UUID.randomUUID();
    UUID hospitalId = UUID.randomUUID();
    UUID erId = UUID.randomUUID();
    UUID alertId = UUID.randomUUID();

    Appointment appt = new Appointment();
    appt.setId(apptId);
    appt.setPatientId(UUID.randomUUID());
    appt.setHospitalId(hospitalId);
    appt.setPriorityLevel(TriageLevel.NORMAL);

    when(alerts.findFirstByAppointmentIdAndHandoverStatusIsNull(apptId))
        .thenReturn(Optional.empty());
    when(appointments.findById(apptId)).thenReturn(Optional.of(appt));
    when(clock.now()).thenReturn(Instant.parse("2026-11-08T08:00:00Z"));
    when(currentUser.userId()).thenReturn(Optional.empty());
    when(currentUser.hasRole("ER")).thenReturn(false);
    when(erStaff.erCandidatesForHospital(hospitalId)).thenReturn(List.of(erId));
    when(users.findEnabledByRole(any())).thenReturn(List.of());
    when(alerts.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              var a = inv.getArgument(0, com.mediflow.shared.domain.RedAlert.class);
              a.setId(alertId);
              return a;
            });
    when(encounters.findByAppointmentId(apptId)).thenReturn(Optional.empty());
    when(patients.findById(appt.getPatientId())).thenReturn(Optional.empty());

    service.raise(apptId, "chest pain", "NURSE_TRIAGE");

    ArgumentCaptor<UUID> assignee = ArgumentCaptor.forClass(UUID.class);
    verify(alerts)
        .updateExt(
            eq(alertId),
            assignee.capture(),
            eq("NURSE_TRIAGE"),
            isNull(),
            isNull(),
            isNull(),
            isNull());
    assertEquals(erId, assignee.getValue());
  }
}
