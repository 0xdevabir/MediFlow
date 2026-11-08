package com.mediflow.appointment.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.mediflow.appointment.booking.ApptSlotQueryRepository;
import com.mediflow.appointment.booking.FreeSlot;
import com.mediflow.patient.service.PatientAppointmentGlancePort.NextVisit;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AppointmentStatus;
import com.mediflow.shared.port.ClockPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentPatientGlancePortTest {

  @Mock ApptAppointmentRepository appointments;
  @Mock ApptSlotQueryRepository slots;
  @Mock ClockPort clock;
  @InjectMocks AppointmentPatientGlancePort port;

  @Test
  void nextUpcomingPicksEarliestFutureBookedSlot() {
    UUID patientId = UUID.randomUUID();
    UUID slotA = UUID.randomUUID();
    UUID slotB = UUID.randomUUID();
    Instant now = Instant.parse("2026-11-08T08:00:00Z");
    Instant later = Instant.parse("2026-11-10T09:00:00Z");
    Instant sooner = Instant.parse("2026-11-09T09:00:00Z");

    Appointment a = appt(slotA, AppointmentStatus.BOOKED);
    Appointment b = appt(slotB, AppointmentStatus.BOOKED);

    when(clock.now()).thenReturn(now);
    when(appointments.findByPatientIdOrderByBookedAtDesc(patientId)).thenReturn(List.of(a, b));
    when(slots.findView(slotA))
        .thenReturn(
            Optional.of(
                new FreeSlot(
                    slotA,
                    later,
                    later.plusSeconds(1800),
                    1,
                    1,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Dr Late",
                    UUID.randomUUID(),
                    "City Hospital",
                    UUID.randomUUID(),
                    "OPD")));
    when(slots.findView(slotB))
        .thenReturn(
            Optional.of(
                new FreeSlot(
                    slotB,
                    sooner,
                    sooner.plusSeconds(1800),
                    1,
                    1,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Dr Soon",
                    UUID.randomUUID(),
                    "City Hospital",
                    UUID.randomUUID(),
                    "OPD")));

    Optional<NextVisit> next = port.nextUpcoming(patientId);
    assertTrue(next.isPresent());
    assertEquals(sooner, next.get().when());
    assertTrue(next.get().summary().contains("Dr Soon"));
  }

  private static Appointment appt(UUID slotId, AppointmentStatus status) {
    Appointment a = new Appointment();
    a.setId(UUID.randomUUID());
    a.setSlotId(slotId);
    a.setStatus(status);
    return a;
  }
}
