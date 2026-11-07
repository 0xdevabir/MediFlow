package com.mediflow.appointment.queue;

import com.mediflow.appointment.booking.BookingMeta;
import com.mediflow.appointment.booking.BookingMetaRepository;
import com.mediflow.appointment.support.AppointmentException;
import com.mediflow.appointment.support.ApptAppointmentRepository;
import com.mediflow.appointment.support.ApptPatientRepository;
import com.mediflow.appointment.support.CheckinCodes;
import com.mediflow.appointment.support.QueueChanged;
import com.mediflow.appointment.support.QueueClock;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AppointmentStatus;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.EncounterStatus;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.QueueEntry;
import com.mediflow.shared.domain.QueueStatus;
import com.mediflow.shared.domain.Slot;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.SlotRepository;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Front-desk check-in: verify identity (QR / two identifiers), issue a daily token, open an
 * encounter shell, and place the patient on the OPD queue.
 */
@Service
public class CheckinService {

  private final ApptAppointmentRepository appointments;
  private final BookingMetaRepository metas;
  private final ApptPatientRepository patients;
  private final SlotRepository slots;
  private final TokenCounterRepository tokenCounters;
  private final ApptQueueEntryRepository queueEntries;
  private final QueueMetaRepository queueMetas;
  private final ApptEncounterRepository encounters;
  private final QueueSseHub sseHub;
  private final CurrentUser currentUser;
  private final ApplicationEventPublisher events;
  private final AuditPort audit;
  private final ClockPort clock;

  public CheckinService(
      ApptAppointmentRepository appointments,
      BookingMetaRepository metas,
      ApptPatientRepository patients,
      SlotRepository slots,
      TokenCounterRepository tokenCounters,
      ApptQueueEntryRepository queueEntries,
      QueueMetaRepository queueMetas,
      ApptEncounterRepository encounters,
      QueueSseHub sseHub,
      CurrentUser currentUser,
      ApplicationEventPublisher events,
      AuditPort audit,
      ClockPort clock) {
    this.appointments = appointments;
    this.metas = metas;
    this.patients = patients;
    this.slots = slots;
    this.tokenCounters = tokenCounters;
    this.queueEntries = queueEntries;
    this.queueMetas = queueMetas;
    this.encounters = encounters;
    this.sseHub = sseHub;
    this.currentUser = currentUser;
    this.events = events;
    this.audit = audit;
    this.clock = clock;
  }

  /** Check-in by the QR / printed check-in code. */
  @PreAuthorize("hasRole('FRONT_DESK')")
  @Transactional
  public CheckinResult checkInByCode(String rawCode) {
    String code = CheckinCodes.normalise(rawCode);
    if (code.isEmpty()) {
      throw new AppointmentException("appointment.error.checkinCodeRequired");
    }
    BookingMeta meta =
        metas
            .findByCheckinCode(code)
            .orElseThrow(() -> new AppointmentException("appointment.error.checkinCodeInvalid"));
    return checkIn(meta.getAppointmentId(), true, null, null);
  }

  /**
   * Check-in with two patient identifiers (MRN + national id, or MRN + phone). The appointment must
   * already be known (selected from the day's booked list).
   */
  @PreAuthorize("hasRole('FRONT_DESK')")
  @Transactional
  public CheckinResult checkInWithIdentifiers(
      UUID appointmentId, String identifierA, String identifierB) {
    return checkIn(appointmentId, false, identifierA, identifierB);
  }

  private CheckinResult checkIn(
      UUID appointmentId, boolean viaCode, String identifierA, String identifierB) {
    Appointment appt =
        appointments
            .findById(appointmentId)
            .orElseThrow(() -> new AppointmentException("appointment.error.notFound"));
    if (appt.getStatus() == AppointmentStatus.CHECKED_IN
        || appt.getStatus() == AppointmentStatus.IN_PROGRESS) {
      throw new AppointmentException("appointment.error.alreadyCheckedIn");
    }
    if (appt.getStatus() != AppointmentStatus.BOOKED) {
      throw new AppointmentException("appointment.error.notBooked");
    }
    if (appt.getDoctorId() == null) {
      throw new AppointmentException("appointment.error.noDoctor");
    }

    Patient patient =
        patients
            .findById(appt.getPatientId())
            .orElseThrow(() -> new AppointmentException("appointment.error.patientNotFound"));
    if (!viaCode) {
      verifyTwoIdentifiers(patient, identifierA, identifierB);
    }

    Instant now = clock.now();
    LocalDate queueDate = QueueClock.queueDate(now);
    int tokenSeq = nextToken(appt.getDoctorId(), queueDate);
    String tokenNo = formatToken(tokenSeq);

    Instant slotStarts = null;
    if (appt.getSlotId() != null) {
      slotStarts = slots.findById(appt.getSlotId()).map(Slot::getStartsAt).orElse(null);
    }

    QueueEntry entry = new QueueEntry();
    entry.setAppointmentId(appt.getId());
    entry.setHospitalId(appt.getHospitalId());
    entry.setTokenNo(tokenNo);
    entry.setPosition(tokenSeq);
    TriageLevel level =
        appt.getPriorityLevel() == null ? TriageLevel.NORMAL : appt.getPriorityLevel();
    entry.setPriorityLevel(level);
    entry.setStatus(QueueStatus.WAITING);
    try {
      entry = queueEntries.saveAndFlush(entry);
    } catch (DataIntegrityViolationException e) {
      throw new AppointmentException("appointment.error.alreadyCheckedIn");
    }

    QueueMeta meta = new QueueMeta();
    meta.setQueueEntryId(entry.getId());
    meta.setAppointmentId(appt.getId());
    meta.setDoctorId(appt.getDoctorId());
    meta.setQueueDate(queueDate);
    meta.setTokenSeq(tokenSeq);
    meta.setArrivedAt(now);
    meta.setSlotStartsAt(slotStarts);
    queueMetas.save(meta);

    Encounter encounter = new Encounter();
    encounter.setPatientId(appt.getPatientId());
    encounter.setDoctorId(appt.getDoctorId());
    encounter.setHospitalId(appt.getHospitalId());
    encounter.setAppointmentId(appt.getId());
    encounter.setStatus(EncounterStatus.IN_PROGRESS);
    encounter.setStartedAt(now);
    encounter = encounters.save(encounter);

    appt.setStatus(AppointmentStatus.CHECKED_IN);
    appt.setCheckedInAt(now);
    appointments.save(appt);

    UUID actor = currentUser.userId().orElse(null);
    audit.record(
        actor,
        appt.getPatientId(),
        "APPOINTMENT_CHECKED_IN",
        "Appointment",
        appt.getId(),
        details(
            "tokenNo",
            tokenNo,
            "tokenSeq",
            tokenSeq,
            "viaCode",
            viaCode,
            "encounterId",
            encounter.getId(),
            "queueEntryId",
            entry.getId()));
    events.publishEvent(
        new DomainEvents.AppointmentCheckedIn(appt.getId(), appt.getPatientId(), now));
    QueueChanged changed = new QueueChanged(appt.getDoctorId(), queueDate);
    events.publishEvent(changed);
    sseHub.publish(changed);

    return new CheckinResult(appt, entry, meta, encounter);
  }

  private int nextToken(UUID doctorId, LocalDate queueDate) {
    TokenCounter counter =
        tokenCounters
            .findForUpdate(doctorId, queueDate)
            .orElseGet(
                () -> {
                  TokenCounter created = new TokenCounter();
                  created.setDoctorId(doctorId);
                  created.setQueueDate(queueDate);
                  created.setLastSeq(0);
                  return tokenCounters.saveAndFlush(created);
                });
    // Re-lock after insert race
    counter =
        tokenCounters
            .findForUpdate(doctorId, queueDate)
            .orElse(counter);
    int next = counter.getLastSeq() + 1;
    counter.setLastSeq(next);
    tokenCounters.save(counter);
    return next;
  }

  static String formatToken(int seq) {
    return String.format(Locale.ROOT, "T%03d", seq);
  }

  /**
   * Require two distinct identifiers that each match the patient (MRN, national id, or phone).
   * Comparison is case-insensitive and ignores spaces/dashes.
   */
  static void verifyTwoIdentifiers(Patient patient, String a, String b) {
    String first = normaliseId(a);
    String second = normaliseId(b);
    if (first.isEmpty() || second.isEmpty()) {
      throw new AppointmentException("appointment.error.identifiersRequired");
    }
    if (first.equals(second)) {
      throw new AppointmentException("appointment.error.identifiersDistinct");
    }
    if (!matchesPatient(patient, first) || !matchesPatient(patient, second)) {
      throw new AppointmentException("appointment.error.identityMismatch");
    }
  }

  private static boolean matchesPatient(Patient patient, String value) {
    return eq(normaliseId(patient.getMrn()), value)
        || eq(normaliseId(patient.getNationalId()), value)
        || eq(normaliseId(patient.getPhone()), value);
  }

  private static boolean eq(String left, String right) {
    return !left.isEmpty() && left.equals(right);
  }

  private static String normaliseId(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.trim().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
  }

  static Map<String, Object> details(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      if (kv[i + 1] != null) {
        m.put(String.valueOf(kv[i]), kv[i + 1]);
      }
    }
    return m;
  }

  public record CheckinResult(
      Appointment appointment, QueueEntry queueEntry, QueueMeta meta, Encounter encounter) {
    public CheckinResult {
      Objects.requireNonNull(appointment);
      Objects.requireNonNull(queueEntry);
      Objects.requireNonNull(meta);
      Objects.requireNonNull(encounter);
    }
  }
}
