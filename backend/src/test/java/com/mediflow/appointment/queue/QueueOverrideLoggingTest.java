package com.mediflow.appointment.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.appointment.booking.BookingMetaRepository;
import com.mediflow.appointment.support.AppointmentAccess;
import com.mediflow.appointment.support.AppointmentException;
import com.mediflow.appointment.support.ApptAppointmentRepository;
import com.mediflow.appointment.support.ApptPatientRepository;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.QueueEntry;
import com.mediflow.shared.domain.QueueStatus;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
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
class QueueOverrideLoggingTest {

  @Mock QueueMetaRepository metas;
  @Mock ApptQueueEntryRepository entries;
  @Mock ApptAppointmentRepository appointments;
  @Mock BookingMetaRepository bookingMetas;
  @Mock ApptPatientRepository patients;
  @Mock QueueOverrideLogRepository overrides;
  @Mock QueueSseHub sseHub;
  @Mock AppointmentAccess access;
  @Mock CurrentUser currentUser;
  @Mock ApplicationEventPublisher events;
  @Mock AuditPort audit;
  @Mock ClockPort clock;

  QueueService service;
  UUID doctorId = UUID.randomUUID();
  LocalDate day = LocalDate.of(2026, 11, 7);
  UUID movingId = UUID.randomUUID();
  UUID targetId = UUID.randomUUID();
  UUID movingAppt = UUID.randomUUID();
  UUID targetAppt = UUID.randomUUID();
  UUID actor = UUID.randomUUID();
  UUID patientId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new QueueService(
            metas,
            entries,
            appointments,
            bookingMetas,
            patients,
            overrides,
            sseHub,
            access,
            currentUser,
            events,
            audit,
            clock);
    when(clock.now()).thenReturn(Instant.parse("2026-11-07T10:00:00Z"));
    when(currentUser.userId()).thenReturn(Optional.of(actor));
  }

  @Test
  void requiresReasonAndWritesOverrideLogPlusAudit() {
    QueueMeta moving = meta(movingId, movingAppt, 2, Instant.parse("2026-11-07T09:02:00Z"));
    QueueMeta target = meta(targetId, targetAppt, 1, Instant.parse("2026-11-07T09:01:00Z"));
    when(metas.findByQueueEntryId(movingId)).thenReturn(Optional.of(moving));
    when(metas.findByQueueEntryId(targetId)).thenReturn(Optional.of(target));
    when(metas.findByDoctorIdAndQueueDate(doctorId, day)).thenReturn(List.of(moving, target));
    when(entries.findByIdIn(any())).thenReturn(List.of(entry(movingId), entry(targetId)));
    when(entries.findById(movingId)).thenReturn(Optional.of(entry(movingId)));
    when(entries.findById(targetId)).thenReturn(Optional.of(entry(targetId)));
    Appointment appt = new Appointment();
    appt.setId(movingAppt);
    appt.setPatientId(patientId);
    when(appointments.findById(movingAppt)).thenReturn(Optional.of(appt));

    AppointmentException missing =
        assertThrows(
            AppointmentException.class,
            () -> service.reorder(movingId, targetId, false, "ab"));
    assertEquals("appointment.error.overrideReasonRequired", missing.messageKey());

    service.reorder(movingId, targetId, false, "Clinical urgency — chest pain");

    ArgumentCaptor<QueueOverrideLog> logCap = ArgumentCaptor.forClass(QueueOverrideLog.class);
    verify(overrides).save(logCap.capture());
    QueueOverrideLog log = logCap.getValue();
    assertEquals("MOVE_BEFORE", log.getAction());
    assertEquals(actor, log.getActorUserId());
    assertEquals("Clinical urgency — chest pain", log.getReason());
    assertEquals(movingId, log.getQueueEntryId());

    verify(audit)
        .record(
            eq(actor),
            eq(patientId),
            eq("QUEUE_OVERRIDE"),
            eq("QueueEntry"),
            eq(movingId),
            any());
  }

  private QueueMeta meta(UUID entryId, UUID apptId, int seq, Instant arrived) {
    QueueMeta m = new QueueMeta();
    m.setQueueEntryId(entryId);
    m.setAppointmentId(apptId);
    m.setDoctorId(doctorId);
    m.setQueueDate(day);
    m.setTokenSeq(seq);
    m.setArrivedAt(arrived);
    return m;
  }

  private QueueEntry entry(UUID id) {
    QueueEntry e = new QueueEntry();
    e.setId(id);
    e.setStatus(QueueStatus.WAITING);
    e.setPriorityLevel(TriageLevel.NORMAL);
    e.setTokenNo("T00" + id.toString().charAt(0));
    e.setPosition(1);
    e.setHospitalId(UUID.randomUUID());
    e.setAppointmentId(UUID.randomUUID());
    return e;
  }
}
