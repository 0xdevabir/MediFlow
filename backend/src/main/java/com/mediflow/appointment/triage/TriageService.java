package com.mediflow.appointment.triage;

import com.mediflow.appointment.queue.QueueService;
import com.mediflow.appointment.redalert.RedAlertService;
import com.mediflow.appointment.support.AppointmentException;
import com.mediflow.appointment.support.ApptAppointmentRepository;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AppointmentStatus;
import com.mediflow.shared.domain.Triage;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nurse triage: CRITICAL / URGENT / NORMAL + symptom severity. No formula — clinical judgement.
 * CRITICAL triggers {@link RedAlertService#raise}.
 */
@Service
public class TriageService {

  private static final Set<String> SEVERITIES =
      Set.of("MILD", "MODERATE", "SEVERE", "LIFE_THREATENING");

  private final ApptTriageRepository triages;
  private final TriageHistoryRepository history;
  private final ApptAppointmentRepository appointments;
  private final QueueService queueService;
  private final RedAlertService redAlerts;
  private final CurrentUser currentUser;
  private final ApplicationEventPublisher events;
  private final AuditPort audit;
  private final ClockPort clock;

  public TriageService(
      ApptTriageRepository triages,
      TriageHistoryRepository history,
      ApptAppointmentRepository appointments,
      QueueService queueService,
      RedAlertService redAlerts,
      CurrentUser currentUser,
      ApplicationEventPublisher events,
      AuditPort audit,
      ClockPort clock) {
    this.triages = triages;
    this.history = history;
    this.appointments = appointments;
    this.queueService = queueService;
    this.redAlerts = redAlerts;
    this.currentUser = currentUser;
    this.events = events;
    this.audit = audit;
    this.clock = clock;
  }

  @PreAuthorize("hasRole('NURSE')")
  @Transactional
  public Triage setLevel(
      UUID appointmentId, TriageLevel level, String symptomSeverity, String note) {
    if (level == null) {
      throw new AppointmentException("appointment.error.triageLevelRequired");
    }
    String severity = normaliseSeverity(symptomSeverity);
    Appointment appt =
        appointments
            .findById(appointmentId)
            .orElseThrow(() -> new AppointmentException("appointment.error.notFound"));
    if (appt.getStatus() != AppointmentStatus.CHECKED_IN
        && appt.getStatus() != AppointmentStatus.IN_PROGRESS
        && appt.getStatus() != AppointmentStatus.BOOKED) {
      throw new AppointmentException("appointment.error.notTriagable");
    }

    Instant now = clock.now();
    UUID actor = currentUser.userId().orElse(null);

    Triage triage =
        triages
            .findByAppointmentId(appointmentId)
            .orElseGet(
                () -> {
                  Triage t = new Triage();
                  t.setAppointmentId(appointmentId);
                  return t;
                });
    triage.setLevel(level);
    triage.setSetBy(actor);
    triage.setNote(trimToNull(note));
    triage.setSetAt(now);
    triage = triages.save(triage);

    TriageHistory row = new TriageHistory();
    row.setAppointmentId(appointmentId);
    row.setLevel(level);
    row.setSymptomSeverity(severity);
    row.setSource("NURSE");
    row.setSetBy(actor);
    row.setNote(trimToNull(note));
    row.setSetAt(now);
    history.save(row);

    appt.setPriorityLevel(level);
    if (level == TriageLevel.CRITICAL || level == TriageLevel.URGENT) {
      appt.setEmergencyLevel(level);
    }
    appointments.save(appt);

    queueService.applyTriageLevel(appointmentId, level, null);

    audit.record(
        actor,
        appt.getPatientId(),
        "TRIAGE_LEVEL_SET",
        "Triage",
        triage.getId(),
        details(
            "level",
            level.name(),
            "symptomSeverity",
            severity,
            "appointmentId",
            appointmentId));
    events.publishEvent(
        new DomainEvents.TriageLevelSet(appointmentId, appt.getPatientId(), level, now));

    if (level == TriageLevel.CRITICAL) {
      redAlerts.raise(
          appointmentId,
          trimToNull(note) == null ? "Nurse triage CRITICAL" : trimToNull(note),
          "NURSE_TRIAGE");
    }
    return triage;
  }

  @PreAuthorize("hasAnyRole('NURSE','FRONT_DESK','DOCTOR','ER','ADMIN')")
  @Transactional(readOnly = true)
  public Triage get(UUID appointmentId) {
    return triages
        .findByAppointmentId(appointmentId)
        .orElseThrow(() -> new AppointmentException("appointment.error.triageNotFound"));
  }

  /**
   * Auto-escalation from vitals (T05). Always raises Red Alert when patient-level vitals are
   * CRITICAL, even if the nurse previously chose a lower triage level.
   */
  @Transactional
  public void escalateFromVitals(UUID appointmentId, String note) {
    Appointment appt =
        appointments
            .findById(appointmentId)
            .orElseThrow(() -> new AppointmentException("appointment.error.notFound"));
    Instant now = clock.now();

    Triage triage =
        triages
            .findByAppointmentId(appointmentId)
            .orElseGet(
                () -> {
                  Triage t = new Triage();
                  t.setAppointmentId(appointmentId);
                  return t;
                });
    triage.setLevel(TriageLevel.CRITICAL);
    triage.setSetBy(null);
    triage.setNote(note);
    triage.setSetAt(now);
    triages.save(triage);

    TriageHistory row = new TriageHistory();
    row.setAppointmentId(appointmentId);
    row.setLevel(TriageLevel.CRITICAL);
    row.setSource("VITALS_AUTO");
    row.setNote(note);
    row.setSetAt(now);
    history.save(row);

    appt.setPriorityLevel(TriageLevel.CRITICAL);
    appt.setEmergencyLevel(TriageLevel.CRITICAL);
    appointments.save(appt);

    queueService.applyTriageLevel(appointmentId, TriageLevel.CRITICAL, null);
    events.publishEvent(
        new DomainEvents.TriageLevelSet(
            appointmentId, appt.getPatientId(), TriageLevel.CRITICAL, now));
    redAlerts.raise(appointmentId, note, "VITALS_AUTO");
  }

  private static String normaliseSeverity(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String s = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    if (!SEVERITIES.contains(s)) {
      throw new AppointmentException("appointment.error.symptomSeverityInvalid");
    }
    return s;
  }

  private static String trimToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() ? null : t;
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
}
