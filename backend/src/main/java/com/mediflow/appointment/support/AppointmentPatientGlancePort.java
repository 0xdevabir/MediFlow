package com.mediflow.appointment.support;

import com.mediflow.appointment.booking.ApptSlotQueryRepository;
import com.mediflow.appointment.booking.FreeSlot;
import com.mediflow.patient.service.PatientAppointmentGlancePort;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AppointmentStatus;
import com.mediflow.shared.port.ClockPort;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Next upcoming booked/checked-in visit for the patient dashboard / home glance tile.
 */
@Component
@Primary
public class AppointmentPatientGlancePort implements PatientAppointmentGlancePort {

  private static final Set<AppointmentStatus> OPEN =
      EnumSet.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS);

  private static final DateTimeFormatter SUMMARY =
      DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault());

  private final ApptAppointmentRepository appointments;
  private final ApptSlotQueryRepository slots;
  private final ClockPort clock;

  public AppointmentPatientGlancePort(
      ApptAppointmentRepository appointments, ApptSlotQueryRepository slots, ClockPort clock) {
    this.appointments = appointments;
    this.slots = slots;
    this.clock = clock;
  }

  @Override
  public Optional<NextVisit> nextUpcoming(UUID patientId) {
    if (patientId == null) {
      return Optional.empty();
    }
    Instant now = clock.now();
    return appointments.findByPatientIdOrderByBookedAtDesc(patientId).stream()
        .filter(a -> a.getStatus() != null && OPEN.contains(a.getStatus()))
        .map(this::toVisit)
        .flatMap(Optional::stream)
        .filter(v -> v.when() != null && !v.when().isBefore(now))
        .min(Comparator.comparing(NextVisit::when));
  }

  private Optional<NextVisit> toVisit(Appointment appt) {
    if (appt.getSlotId() == null) {
      return Optional.empty();
    }
    Optional<FreeSlot> slot = slots.findView(appt.getSlotId());
    if (slot.isEmpty()) {
      return Optional.empty();
    }
    FreeSlot s = slot.get();
    String summary =
        s.doctorName()
            + " · "
            + s.hospitalName()
            + " · "
            + SUMMARY.format(s.startsAt());
    return Optional.of(new NextVisit(appt.getId(), s.startsAt(), summary));
  }
}
