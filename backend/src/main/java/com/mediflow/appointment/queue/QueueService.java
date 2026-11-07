package com.mediflow.appointment.queue;

import com.mediflow.appointment.booking.BookingMeta;
import com.mediflow.appointment.booking.BookingMetaRepository;
import com.mediflow.appointment.support.AppointmentAccess;
import com.mediflow.appointment.support.AppointmentException;
import com.mediflow.appointment.support.ApptAppointmentRepository;
import com.mediflow.appointment.support.ApptPatientRepository;
import com.mediflow.appointment.support.QueueChanged;
import com.mediflow.appointment.support.QueueClock;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AppointmentStatus;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.QueueEntry;
import com.mediflow.shared.domain.QueueStatus;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.domain.VitalClass;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Live OPD queue: board, patient status, doctor call-next, nurse reorder. */
@Service
public class QueueService {

  private final QueueMetaRepository metas;
  private final ApptQueueEntryRepository entries;
  private final ApptAppointmentRepository appointments;
  private final BookingMetaRepository bookingMetas;
  private final ApptPatientRepository patients;
  private final QueueOverrideLogRepository overrides;
  private final QueueSseHub sseHub;
  private final AppointmentAccess access;
  private final CurrentUser currentUser;
  private final ApplicationEventPublisher events;
  private final AuditPort audit;
  private final ClockPort clock;

  public QueueService(
      QueueMetaRepository metas,
      ApptQueueEntryRepository entries,
      ApptAppointmentRepository appointments,
      BookingMetaRepository bookingMetas,
      ApptPatientRepository patients,
      QueueOverrideLogRepository overrides,
      QueueSseHub sseHub,
      AppointmentAccess access,
      CurrentUser currentUser,
      ApplicationEventPublisher events,
      AuditPort audit,
      ClockPort clock) {
    this.metas = metas;
    this.entries = entries;
    this.appointments = appointments;
    this.bookingMetas = bookingMetas;
    this.patients = patients;
    this.overrides = overrides;
    this.sseHub = sseHub;
    this.access = access;
    this.currentUser = currentUser;
    this.events = events;
    this.audit = audit;
    this.clock = clock;
  }

  @PreAuthorize("hasAnyRole('FRONT_DESK','NURSE','DOCTOR','ADMIN')")
  @Transactional(readOnly = true)
  public List<QueueBoardRow> board(UUID doctorId, LocalDate queueDate) {
    LocalDate day = queueDate != null ? queueDate : QueueClock.queueDate(clock.now());
    return buildBoard(doctorId, day, true);
  }

  @PreAuthorize("hasAnyRole('PATIENT','FRONT_DESK','NURSE','DOCTOR','ADMIN')")
  @Transactional(readOnly = true)
  public Optional<QueueBoardRow> statusForAppointment(UUID appointmentId) {
    Appointment appt =
        appointments
            .findById(appointmentId)
            .orElseThrow(() -> new AppointmentException("appointment.error.notFound"));
    access.requireCanView(appt);
    QueueMeta meta = metas.findByAppointmentId(appointmentId).orElse(null);
    if (meta == null) {
      return Optional.empty();
    }
    List<QueueBoardRow> board = buildBoard(meta.getDoctorId(), meta.getQueueDate(), false);
    return board.stream().filter(r -> r.appointmentId().equals(appointmentId)).findFirst();
  }

  @PreAuthorize("hasRole('PATIENT')")
  @Transactional(readOnly = true)
  public Optional<QueueBoardRow> myLiveStatus() {
    UUID patientId = access.requireCurrentPatientId();
    List<Appointment> live =
        appointments.findByPatientIdOrderByBookedAtDesc(patientId).stream()
            .filter(
                a ->
                    a.getStatus() == AppointmentStatus.CHECKED_IN
                        || a.getStatus() == AppointmentStatus.IN_PROGRESS)
            .toList();
    for (Appointment appt : live) {
      Optional<QueueBoardRow> row = statusForAppointment(appt.getId());
      if (row.isPresent()) {
        return row;
      }
    }
    return Optional.empty();
  }

  /** Doctor calls the next WAITING patient (top of the ordered OPD list). */
  @PreAuthorize("hasRole('DOCTOR')")
  @Transactional
  public Optional<QueueBoardRow> callNext(UUID doctorId, LocalDate queueDate) {
    UUID mine = access.requireCurrentDoctorId();
    if (doctorId != null && !doctorId.equals(mine) && !currentUser.hasRole("ADMIN")) {
      throw new AppointmentException("appointment.error.notYourQueue");
    }
    UUID effectiveDoctor = doctorId != null ? doctorId : mine;
    LocalDate day = queueDate != null ? queueDate : QueueClock.queueDate(clock.now());
    List<QueueOrdering.Candidate> ordered = candidates(effectiveDoctor, day);
    if (ordered.isEmpty()) {
      return Optional.empty();
    }
    QueueOrdering.Candidate next = ordered.get(0);
    QueueEntry entry =
        entries
            .findById(next.entryId())
            .orElseThrow(() -> new AppointmentException("appointment.error.queueNotFound"));
    entry.setStatus(QueueStatus.CALLED);
    entries.save(entry);

    Appointment appt =
        appointments
            .findById(
                metas
                    .findByQueueEntryId(entry.getId())
                    .orElseThrow(() -> new AppointmentException("appointment.error.queueNotFound"))
                    .getAppointmentId())
            .orElseThrow(() -> new AppointmentException("appointment.error.notFound"));
    if (appt.getStatus() == AppointmentStatus.CHECKED_IN) {
      appt.setStatus(AppointmentStatus.IN_PROGRESS);
      appointments.save(appt);
    }

    audit.record(
        currentUser.userId().orElse(null),
        appt.getPatientId(),
        "QUEUE_CALLED",
        "QueueEntry",
        entry.getId(),
        Map.of("tokenNo", entry.getTokenNo(), "doctorId", effectiveDoctor.toString()));
    notifyChanged(effectiveDoctor, day);
    return statusForAppointment(appt.getId());
  }

  /**
   * Nurse moves {@code movingEntryId} immediately before {@code targetEntryId} (or after when
   * {@code after} is true). Reason is required and logged.
   */
  @PreAuthorize("hasRole('NURSE')")
  @Transactional
  public void reorder(UUID movingEntryId, UUID targetEntryId, boolean after, String reason) {
    String why = reason == null ? "" : reason.trim();
    if (why.length() < 3) {
      throw new AppointmentException("appointment.error.overrideReasonRequired");
    }
    QueueMeta movingMeta =
        metas
            .findByQueueEntryId(movingEntryId)
            .orElseThrow(() -> new AppointmentException("appointment.error.queueNotFound"));
    QueueMeta targetMeta =
        metas
            .findByQueueEntryId(targetEntryId)
            .orElseThrow(() -> new AppointmentException("appointment.error.queueNotFound"));
    if (!movingMeta.getDoctorId().equals(targetMeta.getDoctorId())
        || !movingMeta.getQueueDate().equals(targetMeta.getQueueDate())) {
      throw new AppointmentException("appointment.error.overrideCrossQueue");
    }

    List<QueueOrdering.Candidate> before = candidates(movingMeta.getDoctorId(), movingMeta.getQueueDate());
    int fromPos = QueueOrdering.aheadOf(before, movingEntryId);

    Map<UUID, QueueOrdering.Candidate> byId =
        before.stream().collect(Collectors.toMap(QueueOrdering.Candidate::entryId, Function.identity()));
    QueueOrdering.Candidate target = byId.get(targetEntryId);
    if (target == null) {
      // Target may be CALLED / CRITICAL — use raw candidate from metas
      target = toCandidate(targetMeta, entries.findById(targetEntryId).orElse(null));
      if (target == null || target.level() == TriageLevel.CRITICAL) {
        throw new AppointmentException("appointment.error.overrideTargetInvalid");
      }
    }
    QueueOrdering.SortKey key =
        after ? QueueOrdering.placeAfter(target) : QueueOrdering.placeBefore(target);
    movingMeta.applyOverride(key);
    metas.save(movingMeta);

    List<QueueOrdering.Candidate> afterList =
        candidates(movingMeta.getDoctorId(), movingMeta.getQueueDate());
    int toPos = QueueOrdering.aheadOf(afterList, movingEntryId);
    renumberPositions(afterList);

    Instant now = clock.now();
    UUID actor =
        currentUser
            .userId()
            .orElseThrow(() -> new AppointmentException("appointment.error.noActor"));
    QueueOverrideLog log = new QueueOverrideLog();
    log.setQueueEntryId(movingEntryId);
    log.setAppointmentId(movingMeta.getAppointmentId());
    log.setActorUserId(actor);
    log.setAction(after ? "MOVE_AFTER" : "MOVE_BEFORE");
    log.setFromPosition(fromPos < 0 ? null : fromPos + 1);
    log.setToPosition(toPos < 0 ? null : toPos + 1);
    log.setReason(why);
    log.setOccurredAt(now);
    overrides.save(log);

    audit.record(
        actor,
        appointments
            .findById(movingMeta.getAppointmentId())
            .map(Appointment::getPatientId)
            .orElse(null),
        "QUEUE_OVERRIDE",
        "QueueEntry",
        movingEntryId,
        details(
            "action",
            log.getAction(),
            "fromPosition",
            log.getFromPosition(),
            "toPosition",
            log.getToPosition(),
            "reason",
            why,
            "targetEntryId",
            targetEntryId));
    notifyChanged(movingMeta.getDoctorId(), movingMeta.getQueueDate());
  }

  /** Move the entry one place toward the head of the waiting list. */
  @PreAuthorize("hasRole('NURSE')")
  @Transactional
  public void moveUp(UUID entryId, String reason) {
    QueueMeta meta =
        metas
            .findByQueueEntryId(entryId)
            .orElseThrow(() -> new AppointmentException("appointment.error.queueNotFound"));
    List<QueueOrdering.Candidate> ordered = candidates(meta.getDoctorId(), meta.getQueueDate());
    int idx = QueueOrdering.aheadOf(ordered, entryId);
    if (idx <= 0) {
      throw new AppointmentException("appointment.error.alreadyAtFront");
    }
    UUID target = ordered.get(idx - 1).entryId();
    reorder(entryId, target, false, reason);
  }

  /** Remove a CRITICAL patient from the OPD queue (Red Alert path). */
  @Transactional
  public void removeFromOpd(UUID appointmentId, String reason) {
    QueueMeta meta = metas.findByAppointmentId(appointmentId).orElse(null);
    if (meta == null) {
      return;
    }
    QueueEntry entry = entries.findById(meta.getQueueEntryId()).orElse(null);
    if (entry == null) {
      return;
    }
    if (entry.getStatus() == QueueStatus.WAITING || entry.getStatus() == QueueStatus.CALLED) {
      entry.setStatus(QueueStatus.LEFT);
      entry.setPriorityLevel(TriageLevel.CRITICAL);
      entries.save(entry);
    }
    meta.setRemovedReason(reason);
    metas.save(meta);
    notifyChanged(meta.getDoctorId(), meta.getQueueDate());
  }

  @Transactional
  public void applyTriageLevel(UUID appointmentId, TriageLevel level, VitalClass vitals) {
    QueueMeta meta = metas.findByAppointmentId(appointmentId).orElse(null);
    if (meta == null) {
      return;
    }
    if (vitals != null) {
      meta.setVitalsLevel(vitals);
    }
    QueueEntry entry = entries.findById(meta.getQueueEntryId()).orElse(null);
    if (entry != null && level != null) {
      entry.setPriorityLevel(level);
      if (level == TriageLevel.CRITICAL
          && (entry.getStatus() == QueueStatus.WAITING || entry.getStatus() == QueueStatus.CALLED)) {
        entry.setStatus(QueueStatus.LEFT);
        meta.setRemovedReason("RED_ALERT");
      }
      entries.save(entry);
    }
    metas.save(meta);
    notifyChanged(meta.getDoctorId(), meta.getQueueDate());
  }

  private List<QueueBoardRow> buildBoard(UUID doctorId, LocalDate day, boolean staffLabels) {
    List<QueueOrdering.Candidate> ordered = candidates(doctorId, day);
    List<QueueMeta> allMeta = metas.findByDoctorIdAndQueueDate(doctorId, day);
    Map<UUID, QueueMeta> metaByEntry =
        allMeta.stream().collect(Collectors.toMap(QueueMeta::getQueueEntryId, Function.identity()));
    Map<UUID, QueueEntry> entryById =
        entries.findByIdIn(metaByEntry.keySet()).stream()
            .collect(Collectors.toMap(QueueEntry::getId, Function.identity()));
    Map<UUID, Appointment> apptById =
        appointments
            .findAllById(allMeta.stream().map(QueueMeta::getAppointmentId).toList())
            .stream()
            .collect(Collectors.toMap(Appointment::getId, Function.identity()));
    Map<UUID, BookingMeta> bookingByAppt =
        bookingMetas
            .findByAppointmentIdIn(apptById.keySet())
            .stream()
            .collect(Collectors.toMap(BookingMeta::getAppointmentId, Function.identity()));
    Map<UUID, Patient> patientById = new HashMap<>();
    if (staffLabels) {
      for (Appointment a : apptById.values()) {
        patients.findById(a.getPatientId()).ifPresent(p -> patientById.put(p.getId(), p));
      }
    }

    List<QueueBoardRow> rows = new ArrayList<>();
    for (int i = 0; i < ordered.size(); i++) {
      QueueOrdering.Candidate c = ordered.get(i);
      QueueMeta meta = metaByEntry.get(c.entryId());
      QueueEntry entry = entryById.get(c.entryId());
      if (meta == null || entry == null) {
        continue;
      }
      Appointment appt = apptById.get(meta.getAppointmentId());
      BookingMeta bm = bookingByAppt.get(meta.getAppointmentId());
      String label = "";
      if (staffLabels && appt != null) {
        Patient p = patientById.get(appt.getPatientId());
        label = p == null ? "" : (nullToEmpty(p.getGivenName()) + " " + nullToEmpty(p.getFamilyName())).trim();
        if (p != null && p.getMrn() != null) {
          label = label + " (" + p.getMrn() + ")";
        }
      }
      rows.add(
          new QueueBoardRow(
              entry.getId(),
              meta.getAppointmentId(),
              appt == null ? null : appt.getPatientId(),
              entry.getTokenNo(),
              meta.getTokenSeq(),
              entry.getPriorityLevel(),
              entry.getStatus(),
              meta.getVitalsLevel(),
              meta.getArrivedAt(),
              i + 1,
              i,
              bm != null && bm.isEmergencyFlag(),
              label));
    }
    return rows;
  }

  private List<QueueOrdering.Candidate> candidates(UUID doctorId, LocalDate day) {
    List<QueueMeta> allMeta = metas.findByDoctorIdAndQueueDate(doctorId, day);
    if (allMeta.isEmpty()) {
      return List.of();
    }
    Map<UUID, QueueEntry> entryById =
        entries
            .findByIdIn(allMeta.stream().map(QueueMeta::getQueueEntryId).toList())
            .stream()
            .collect(Collectors.toMap(QueueEntry::getId, Function.identity()));
    List<QueueOrdering.Candidate> all = new ArrayList<>();
    for (QueueMeta meta : allMeta) {
      QueueOrdering.Candidate c = toCandidate(meta, entryById.get(meta.getQueueEntryId()));
      if (c != null) {
        all.add(c);
      }
    }
    return QueueOrdering.order(all);
  }

  private static QueueOrdering.Candidate toCandidate(QueueMeta meta, QueueEntry entry) {
    if (meta == null || entry == null) {
      return null;
    }
    return new QueueOrdering.Candidate(
        entry.getId(),
        entry.getPriorityLevel(),
        entry.getStatus(),
        meta.getVitalsLevel(),
        meta.getArrivedAt(),
        meta.getSlotStartsAt(),
        meta.getTokenSeq(),
        meta.overrideKey());
  }

  private void renumberPositions(List<QueueOrdering.Candidate> ordered) {
    for (int i = 0; i < ordered.size(); i++) {
      QueueEntry entry = entries.findById(ordered.get(i).entryId()).orElse(null);
      if (entry != null) {
        entry.setPosition(i + 1);
        entries.save(entry);
      }
    }
  }

  private void notifyChanged(UUID doctorId, LocalDate day) {
    QueueChanged changed = new QueueChanged(doctorId, day);
    events.publishEvent(changed);
    sseHub.publish(changed);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
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

  /** Active statuses used when listing today's checked-in appointments for check-in desk. */
  public static EnumSet<AppointmentStatus> liveStatuses() {
    return EnumSet.of(
        AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS, AppointmentStatus.BOOKED);
  }
}
