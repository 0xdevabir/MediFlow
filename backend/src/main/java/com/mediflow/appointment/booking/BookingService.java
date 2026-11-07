package com.mediflow.appointment.booking;

import com.mediflow.appointment.support.AppointmentAccess;
import com.mediflow.appointment.support.AppointmentException;
import com.mediflow.appointment.support.ApptAppointmentRepository;
import com.mediflow.appointment.support.ApptPatientRepository;
import com.mediflow.appointment.support.CheckinCodes;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AppointmentStatus;
import com.mediflow.shared.domain.PractitionerRoleEntity;
import com.mediflow.shared.domain.Slot;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.PractitionerRoleEntityRepository;
import com.mediflow.shared.repo.SlotRepository;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booking, cancel, reschedule and the front-desk emergency flag (process 2.0, flows 3–6).
 *
 * <p>Double-booking is prevented by the slot's JPA {@code @Version}: the counter is incremented
 * and flushed inside the booking transaction, so two concurrent bookings of the last place cannot
 * both commit (the loser gets {@code appointment.error.slotTaken}). A DB check constraint ({@code
 * booked_count <= capacity}) and a partial unique index (one live booking per patient per slot)
 * back this up.
 */
@Service
public class BookingService {

  private final SlotRepository slots;
  private final PractitionerRoleEntityRepository practitionerRoles;
  private final ApptAppointmentRepository appointments;
  private final BookingMetaRepository metas;
  private final ApptPatientRepository patients;
  private final AppointmentAccess access;
  private final CurrentUser currentUser;
  private final ApplicationEventPublisher events;
  private final AuditPort audit;
  private final ClockPort clock;

  public BookingService(
      SlotRepository slots,
      PractitionerRoleEntityRepository practitionerRoles,
      ApptAppointmentRepository appointments,
      BookingMetaRepository metas,
      ApptPatientRepository patients,
      AppointmentAccess access,
      CurrentUser currentUser,
      ApplicationEventPublisher events,
      AuditPort audit,
      ClockPort clock) {
    this.slots = slots;
    this.practitionerRoles = practitionerRoles;
    this.appointments = appointments;
    this.metas = metas;
    this.patients = patients;
    this.access = access;
    this.currentUser = currentUser;
    this.events = events;
    this.audit = audit;
    this.clock = clock;
  }

  /** Patient books for themself. */
  @PreAuthorize("hasRole('PATIENT')")
  @Transactional
  public Appointment bookForCurrentPatient(UUID slotId) {
    return book(access.requireCurrentPatientId(), slotId, false, null, null);
  }

  /** Front desk books on the patient's behalf, optionally flagging a suspected emergency. */
  @PreAuthorize("hasRole('FRONT_DESK')")
  @Transactional
  public Appointment bookOnBehalf(
      UUID patientId, UUID slotId, boolean suspectedEmergency, String emergencyNote) {
    patients
        .findById(patientId)
        .orElseThrow(() -> new AppointmentException("appointment.error.patientNotFound"));
    return book(patientId, slotId, suspectedEmergency, emergencyNote, null);
  }

  @PreAuthorize("hasAnyRole('PATIENT','FRONT_DESK','ADMIN')")
  @Transactional
  public void cancel(UUID appointmentId) {
    Appointment appt = load(appointmentId);
    access.requireCanChange(appt);
    cancelInternal(appt, "CANCELLED");
  }

  /** Books the new slot first, then cancels the old appointment (new id, same patient). */
  @PreAuthorize("hasAnyRole('PATIENT','FRONT_DESK','ADMIN')")
  @Transactional
  public Appointment reschedule(UUID appointmentId, UUID newSlotId) {
    Appointment old = load(appointmentId);
    access.requireCanChange(old);
    if (old.getStatus() != AppointmentStatus.BOOKED) {
      throw new AppointmentException("appointment.error.notChangeable");
    }
    if (newSlotId.equals(old.getSlotId())) {
      throw new AppointmentException("appointment.error.sameSlot");
    }
    BookingMeta oldMeta = metas.findByAppointmentId(old.getId()).orElse(null);
    boolean flag = oldMeta != null && oldMeta.isEmergencyFlag();
    String note = oldMeta == null ? null : oldMeta.getEmergencyNote();
    Appointment fresh = book(old.getPatientId(), newSlotId, flag, note, old.getId());
    cancelInternal(old, "RESCHEDULED");
    Instant newStart =
        fresh.getSlotId() == null
            ? clock.now()
            : slots.findById(fresh.getSlotId()).map(s -> s.getStartsAt()).orElse(clock.now());
    events.publishEvent(
        new DomainEvents.ScheduleChanged(
            fresh.getId(), fresh.getPatientId(), newStart, clock.now()));
    return fresh;
  }

  /** Front desk / nurse marks a suspected emergency at booking or arrival (§6.2 step 1). */
  @PreAuthorize("hasAnyRole('FRONT_DESK','NURSE')")
  @Transactional
  public void flagEmergency(UUID appointmentId, String note) {
    Appointment appt = load(appointmentId);
    if (appt.getStatus() == AppointmentStatus.CANCELLED
        || appt.getStatus() == AppointmentStatus.COMPLETED) {
      throw new AppointmentException("appointment.error.notChangeable");
    }
    BookingMeta meta = metaFor(appt);
    meta.setEmergencyFlag(true);
    meta.setEmergencyFlaggedBy(currentUser.userId().orElse(null));
    meta.setEmergencyFlaggedAt(clock.now());
    meta.setEmergencyNote(trimToNull(note));
    metas.save(meta);
    audit.record(
        currentUser.userId().orElse(null),
        appt.getPatientId(),
        "APPOINTMENT_EMERGENCY_FLAGGED",
        "Appointment",
        appt.getId(),
        details("note", trimToNull(note)));
  }

  @PreAuthorize("hasRole('PATIENT')")
  @Transactional(readOnly = true)
  public List<Appointment> myAppointments() {
    UUID patientId = access.requireCurrentPatientId();
    List<Appointment> list = appointments.findByPatientIdOrderByBookedAtDesc(patientId);
    audit.record(
        currentUser.userId().orElse(null),
        patientId,
        "APPOINTMENT_LIST_READ",
        "Appointment",
        null,
        Map.of("count", list.size()));
    return list;
  }

  @PreAuthorize("hasAnyRole('FRONT_DESK','NURSE','ADMIN')")
  @Transactional(readOnly = true)
  public List<Appointment> appointmentsForPatient(UUID patientId) {
    List<Appointment> list = appointments.findByPatientIdOrderByBookedAtDesc(patientId);
    audit.record(
        currentUser.userId().orElse(null),
        patientId,
        "APPOINTMENT_LIST_READ",
        "Appointment",
        null,
        Map.of("count", list.size()));
    return list;
  }

  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public Appointment get(UUID appointmentId) {
    Appointment appt = load(appointmentId);
    access.requireCanView(appt);
    return appt;
  }

  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public Optional<BookingMeta> meta(UUID appointmentId) {
    Appointment appt = load(appointmentId);
    access.requireCanView(appt);
    return metas.findByAppointmentId(appointmentId);
  }

  // ---------------------------------------------------------------------------

  Appointment book(
      UUID patientId, UUID slotId, boolean emergency, String emergencyNote, UUID rescheduledFrom) {
    Instant now = clock.now();
    Slot slot =
        slots
            .findById(slotId)
            .orElseThrow(() -> new AppointmentException("appointment.error.slotNotFound"));
    if (!slot.getStartsAt().isAfter(now)) {
      throw new AppointmentException("appointment.error.slotPast");
    }
    if (slot.getBookedCount() >= slot.getCapacity()) {
      throw new AppointmentException("appointment.error.slotFull");
    }
    if (appointments.existsByPatientIdAndSlotIdAndStatusNot(
        patientId, slotId, AppointmentStatus.CANCELLED)) {
      throw new AppointmentException("appointment.error.alreadyBooked");
    }
    PractitionerRoleEntity role =
        practitionerRoles
            .findById(slot.getPractitionerRoleId())
            .filter(PractitionerRoleEntity::getActive)
            .orElseThrow(() -> new AppointmentException("appointment.error.slotNotFound"));

    slot.setBookedCount(slot.getBookedCount() + 1);
    try {
      slots.saveAndFlush(slot); // @Version check: concurrent booking of the same slot fails here
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new AppointmentException("appointment.error.slotTaken");
    } catch (DataIntegrityViolationException e) {
      throw new AppointmentException("appointment.error.slotFull");
    }

    Appointment appt = new Appointment();
    appt.setPatientId(patientId);
    appt.setDoctorId(role.getPractitionerId());
    appt.setHospitalId(role.getHospitalId());
    appt.setSlotId(slot.getId());
    appt.setStatus(AppointmentStatus.BOOKED);
    appt.setPriorityLevel(TriageLevel.NORMAL);
    appt.setBookedAt(now);
    try {
      appt = appointments.saveAndFlush(appt);
    } catch (DataIntegrityViolationException e) {
      throw new AppointmentException("appointment.error.alreadyBooked");
    }

    UUID actor = currentUser.userId().orElse(null);
    BookingMeta meta = new BookingMeta();
    meta.setAppointmentId(appt.getId());
    meta.setBookedBy(actor);
    meta.setCheckinCode(CheckinCodes.next());
    meta.setRescheduledFrom(rescheduledFrom);
    if (emergency) {
      meta.setEmergencyFlag(true);
      meta.setEmergencyFlaggedBy(actor);
      meta.setEmergencyFlaggedAt(now);
      meta.setEmergencyNote(trimToNull(emergencyNote));
    }
    metas.save(meta);

    audit.record(
        actor,
        patientId,
        "APPOINTMENT_BOOKED",
        "Appointment",
        appt.getId(),
        details(
            "slotId", slotId,
            "doctorId", appt.getDoctorId(),
            "emergencyFlag", emergency,
            "rescheduledFrom", rescheduledFrom));
    events.publishEvent(
        new DomainEvents.AppointmentBooked(appt.getId(), patientId, appt.getHospitalId(), now));
    return appt;
  }

  private void cancelInternal(Appointment appt, String reason) {
    if (appt.getStatus() != AppointmentStatus.BOOKED) {
      throw new AppointmentException("appointment.error.notChangeable");
    }
    Instant now = clock.now();
    if (appt.getSlotId() != null) {
      slots
          .findById(appt.getSlotId())
          .ifPresent(
              slot -> {
                slot.setBookedCount(Math.max(0, slot.getBookedCount() - 1));
                try {
                  slots.saveAndFlush(slot);
                } catch (ObjectOptimisticLockingFailureException e) {
                  throw new AppointmentException("appointment.error.tryAgain");
                }
              });
    }
    appt.setStatus(AppointmentStatus.CANCELLED);
    appt.setCancelledAt(now);
    appointments.save(appt);
    audit.record(
        currentUser.userId().orElse(null),
        appt.getPatientId(),
        "APPOINTMENT_CANCELLED",
        "Appointment",
        appt.getId(),
        details("reason", reason));
    events.publishEvent(
        new DomainEvents.AppointmentCancelled(appt.getId(), appt.getPatientId(), now));
  }

  private Appointment load(UUID id) {
    return appointments
        .findById(id)
        .orElseThrow(() -> new AppointmentException("appointment.error.notFound"));
  }

  private BookingMeta metaFor(Appointment appt) {
    return metas
        .findByAppointmentId(appt.getId())
        .orElseGet(
            () -> {
              BookingMeta m = new BookingMeta();
              m.setAppointmentId(appt.getId());
              m.setCheckinCode(CheckinCodes.next());
              return m;
            });
  }

  static String trimToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  /** Map.of rejects nulls; audit details may legitimately carry them. */
  static Map<String, Object> details(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      if (kv[i + 1] != null) {
        m.put(String.valueOf(kv[i]), kv[i + 1]);
      }
    }
    return m;
  }
}
