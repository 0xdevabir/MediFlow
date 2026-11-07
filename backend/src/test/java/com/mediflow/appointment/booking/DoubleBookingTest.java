package com.mediflow.appointment.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.appointment.support.AppointmentAccess;
import com.mediflow.appointment.support.AppointmentException;
import com.mediflow.appointment.support.ApptAppointmentRepository;
import com.mediflow.appointment.support.ApptPatientRepository;
import com.mediflow.shared.domain.AppointmentStatus;
import com.mediflow.shared.domain.PractitionerRoleEntity;
import com.mediflow.shared.domain.Slot;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.PractitionerRoleEntityRepository;
import com.mediflow.shared.repo.SlotRepository;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class DoubleBookingTest {

  @Mock SlotRepository slots;
  @Mock PractitionerRoleEntityRepository roles;
  @Mock ApptAppointmentRepository appointments;
  @Mock BookingMetaRepository metas;
  @Mock ApptPatientRepository patients;
  @Mock AppointmentAccess access;
  @Mock CurrentUser currentUser;
  @Mock ApplicationEventPublisher events;
  @Mock AuditPort audit;
  @Mock ClockPort clock;

  BookingService service;
  UUID slotId = UUID.randomUUID();
  UUID patientId = UUID.randomUUID();
  UUID roleId = UUID.randomUUID();
  UUID doctorId = UUID.randomUUID();
  UUID hospitalId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new BookingService(
            slots,
            roles,
            appointments,
            metas,
            patients,
            access,
            currentUser,
            events,
            audit,
            clock);
    when(clock.now()).thenReturn(Instant.parse("2026-11-07T10:00:00Z"));
  }

  @Test
  void rejectsWhenSlotAlreadyFull() {
    Slot slot = slot(1, 1);
    when(slots.findById(slotId)).thenReturn(Optional.of(slot));

    AppointmentException ex =
        assertThrows(
            AppointmentException.class,
            () -> service.book(patientId, slotId, false, null, null));
    assertEquals("appointment.error.slotFull", ex.messageKey());
    verify(appointments, never()).saveAndFlush(any());
  }

  @Test
  void mapsOptimisticLockToSlotTaken() {
    Slot slot = slot(2, 1);
    when(slots.findById(slotId)).thenReturn(Optional.of(slot));
    when(appointments.existsByPatientIdAndSlotIdAndStatusNot(
            patientId, slotId, AppointmentStatus.CANCELLED))
        .thenReturn(false);
    PractitionerRoleEntity role = new PractitionerRoleEntity();
    role.setId(roleId);
    role.setPractitionerId(doctorId);
    role.setHospitalId(hospitalId);
    role.setActive(true);
    when(roles.findById(roleId)).thenReturn(Optional.of(role));
    when(slots.saveAndFlush(slot))
        .thenThrow(new ObjectOptimisticLockingFailureException(Slot.class, slotId));

    AppointmentException ex =
        assertThrows(
            AppointmentException.class,
            () -> service.book(patientId, slotId, false, null, null));
    assertEquals("appointment.error.slotTaken", ex.messageKey());
  }

  @Test
  void rejectsDuplicateLiveBookingSameSlot() {
    Slot slot = slot(2, 0);
    when(slots.findById(slotId)).thenReturn(Optional.of(slot));
    when(appointments.existsByPatientIdAndSlotIdAndStatusNot(
            patientId, slotId, AppointmentStatus.CANCELLED))
        .thenReturn(true);

    AppointmentException ex =
        assertThrows(
            AppointmentException.class,
            () -> service.book(patientId, slotId, false, null, null));
    assertEquals("appointment.error.alreadyBooked", ex.messageKey());
  }

  private Slot slot(int capacity, int booked) {
    Slot s = new Slot();
    s.setId(slotId);
    s.setPractitionerRoleId(roleId);
    s.setStartsAt(Instant.parse("2026-11-08T04:00:00Z"));
    s.setEndsAt(Instant.parse("2026-11-08T04:30:00Z"));
    s.setCapacity(capacity);
    s.setBookedCount(booked);
    return s;
  }
}
