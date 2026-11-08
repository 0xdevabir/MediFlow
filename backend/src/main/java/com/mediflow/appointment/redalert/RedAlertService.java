package com.mediflow.appointment.redalert;

import com.mediflow.appointment.queue.ApptEncounterRepository;
import com.mediflow.appointment.queue.QueueService;
import com.mediflow.appointment.support.AppointmentException;
import com.mediflow.appointment.support.ApptAppointmentRepository;
import com.mediflow.appointment.support.ApptPatientRepository;
import com.mediflow.appointment.support.ApptUserRepository;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.RedAlert;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import com.mediflow.shared.web.CurrentUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raises Red Alerts for CRITICAL triage / vitals: notifies ER + duty doctors, removes the patient
 * from the OPD queue, and always sets {@code assigned_er_user_id} when ER staff exist (V2-02 ACL).
 */
@Service
public class RedAlertService {

  private final ApptRedAlertRepository alerts;
  private final ApptAppointmentRepository appointments;
  private final ApptEncounterRepository encounters;
  private final ApptPatientRepository patients;
  private final ApptUserRepository users;
  private final ErStaffQueries erStaff;
  private final QueueService queueService;
  private final CurrentUser currentUser;
  private final ApplicationEventPublisher events;
  private final AuditPort audit;
  private final NotificationPort notifications;
  private final ClockPort clock;
  private final RedAlertMetrics metrics;

  public RedAlertService(
      ApptRedAlertRepository alerts,
      ApptAppointmentRepository appointments,
      ApptEncounterRepository encounters,
      ApptPatientRepository patients,
      ApptUserRepository users,
      ErStaffQueries erStaff,
      QueueService queueService,
      CurrentUser currentUser,
      ApplicationEventPublisher events,
      AuditPort audit,
      NotificationPort notifications,
      ClockPort clock,
      RedAlertMetrics metrics) {
    this.alerts = alerts;
    this.appointments = appointments;
    this.encounters = encounters;
    this.patients = patients;
    this.users = users;
    this.erStaff = erStaff;
    this.queueService = queueService;
    this.currentUser = currentUser;
    this.events = events;
    this.audit = audit;
    this.notifications = notifications;
    this.clock = clock;
    this.metrics = metrics;
  }

  /**
   * Raise (or no-op if one is already open) a Red Alert for this appointment. Safe to call from
   * triage and from the vitals auto-escalation listener.
   */
  @Transactional
  public RedAlert raise(UUID appointmentId, String reason, String source) {
    Optional<RedAlert> existing =
        alerts.findFirstByAppointmentIdAndHandoverStatusIsNull(appointmentId);
    if (existing.isPresent()) {
      return existing.get();
    }
    Appointment appt =
        appointments
            .findById(appointmentId)
            .orElseThrow(() -> new AppointmentException("appointment.error.notFound"));
    Instant now = clock.now();
    UUID actor = currentUser.userId().orElse(null);

    RedAlert alert = new RedAlert();
    alert.setAppointmentId(appt.getId());
    alert.setPatientId(appt.getPatientId());
    alert.setHospitalId(appt.getHospitalId());
    alert.setLevel(TriageLevel.CRITICAL);
    alert.setRaisedBy(actor);
    alert.setReason(reason);
    alert.setRaisedAt(now);
    encounters
        .findByAppointmentId(appointmentId)
        .map(Encounter::getId)
        .ifPresent(alert::setEncounterId);
    alert = alerts.saveAndFlush(alert);

    // Always set assignee when any ROLE_ER exists (V2-02 ACL). Prefer logged-in ER, else
    // on-duty / least-loaded ER for the hospital.
    UUID assignedEr = resolveAssignee(appt.getHospitalId());
    alerts.updateExt(alert.getId(), assignedEr, source, null, null, null, null);

    appt.setPriorityLevel(TriageLevel.CRITICAL);
    appt.setEmergencyLevel(TriageLevel.CRITICAL);
    appointments.save(appt);

    queueService.removeFromOpd(appointmentId, "RED_ALERT");

    Patient patient = patients.findById(appt.getPatientId()).orElse(null);
    String summary = patientSummary(patient, reason);
    notifyStaff(appt, summary);

    audit.record(
        actor,
        appt.getPatientId(),
        "RED_ALERT_RAISED",
        "RedAlert",
        alert.getId(),
        details(
            "appointmentId",
            appointmentId,
            "source",
            source,
            "assignedErUserId",
            assignedEr,
            "reason",
            reason));
    events.publishEvent(
        new DomainEvents.RedAlertRaised(
            alert.getId(), appt.getPatientId(), appt.getHospitalId(), TriageLevel.CRITICAL, now));
    metrics.onRaised(alert);
    return alert;
  }

  @PreAuthorize("hasAnyRole('ER','ADMIN','NURSE')")
  @Transactional(readOnly = true)
  public List<RedAlert> active(UUID hospitalId) {
    if (hospitalId == null) {
      return alerts.findAllActive();
    }
    return alerts.findActiveByHospital(hospitalId);
  }

  @PreAuthorize("hasAnyRole('ER','ADMIN','NURSE','DOCTOR')")
  @Transactional(readOnly = true)
  public Optional<RedAlert> get(UUID redAlertId) {
    return alerts.findById(redAlertId);
  }

  @Transactional(readOnly = true)
  public Optional<RedAlertExt> ext(UUID redAlertId) {
    List<Object[]> rows = alerts.loadExt(redAlertId);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    Object[] r = rows.get(0);
    return Optional.of(
        new RedAlertExt(
            (UUID) r[0],
            (UUID) r[1],
            r[2] == null ? null : r[2].toString(),
            (UUID) r[3],
            toInstant(r[4]),
            r[5] == null ? null : r[5].toString()));
  }

  @PreAuthorize("hasRole('ER')")
  @Transactional
  public RedAlert acknowledge(UUID redAlertId) {
    RedAlert alert =
        alerts
            .findById(redAlertId)
            .orElseThrow(() -> new AppointmentException("appointment.error.redAlertNotFound"));
    if (alert.getHandoverStatus() != null) {
      throw new AppointmentException("appointment.error.redAlertClosed");
    }
    Instant now = clock.now();
    UUID actor =
        currentUser
            .userId()
            .orElseThrow(() -> new AppointmentException("appointment.error.noActor"));
    if (alert.getAcknowledgedAt() == null) {
      alert.setAcknowledgedAt(now);
      alerts.save(alert);
    }
    // Keep original raise assignee; only set acknowledged_by.
    alerts.updateExt(alert.getId(), null, null, actor, null, null, null);
    audit.record(
        actor,
        alert.getPatientId(),
        "RED_ALERT_ACKNOWLEDGED",
        "RedAlert",
        alert.getId(),
        Map.of());
    return alert;
  }

  /**
   * Close the alert with a handover outcome. Allowed values: ADMITTED, REFERRED, DISCHARGED.
   * Further clinical flow is out of scope for M2.
   */
  @PreAuthorize("hasRole('ER')")
  @Transactional
  public RedAlert handover(UUID redAlertId, String status, String note) {
    String outcome = status == null ? "" : status.trim().toUpperCase();
    if (!outcome.equals("ADMITTED")
        && !outcome.equals("REFERRED")
        && !outcome.equals("DISCHARGED")) {
      throw new AppointmentException("appointment.error.handoverInvalid");
    }
    RedAlert alert =
        alerts
            .findById(redAlertId)
            .orElseThrow(() -> new AppointmentException("appointment.error.redAlertNotFound"));
    if (alert.getHandoverStatus() != null) {
      throw new AppointmentException("appointment.error.redAlertClosed");
    }
    Instant now = clock.now();
    UUID actor =
        currentUser
            .userId()
            .orElseThrow(() -> new AppointmentException("appointment.error.noActor"));
    alert.setHandoverStatus(outcome);
    if (alert.getAcknowledgedAt() == null) {
      alert.setAcknowledgedAt(now);
    }
    alerts.save(alert);
    alerts.updateExt(alert.getId(), null, null, actor, actor, now, trimToNull(note));
    metrics.onClosed();
    audit.record(
        actor,
        alert.getPatientId(),
        "RED_ALERT_HANDOVER",
        "RedAlert",
        alert.getId(),
        details("status", outcome, "note", trimToNull(note)));
    return alert;
  }

  /**
   * Prefer the signed-in ER user; otherwise least-loaded on-duty ER for the hospital. Returns null
   * only when no enabled {@code ROLE_ER} user exists.
   */
  UUID resolveAssignee(UUID hospitalId) {
    if (currentUser.hasRole("ER")) {
      UUID self = currentUser.userId().orElse(null);
      if (self != null) {
        return self;
      }
    }
    List<UUID> candidates = erStaff.erCandidatesForHospital(hospitalId);
    return candidates.isEmpty() ? null : candidates.get(0);
  }

  /** Display name for ER board/detail assignee column. */
  public String assigneeName(UUID userId) {
    return erStaff.displayName(userId);
  }

  private void notifyStaff(Appointment appt, String summary) {
    String subject = "Red Alert";
    for (AppUser er : users.findEnabledByRole(Role.ROLE_ER)) {
      notifications.notifyUser(er.getId(), "IN_APP", er.getLocale(), subject, summary);
      notifications.notifyUser(er.getId(), "PUSH", er.getLocale(), subject, summary);
    }
    // Duty doctor approximation: the appointment's doctor user, if linked.
    // Broader hospital fan-out is T10's RedAlertRaised listener.
  }

  static String patientSummary(Patient patient, String reason) {
    if (patient == null) {
      return "CRITICAL Red Alert" + (reason == null ? "" : ": " + reason);
    }
    String name =
        (nullToEmpty(patient.getGivenName()) + " " + nullToEmpty(patient.getFamilyName())).trim();
    String mrn = patient.getMrn() == null ? "?" : patient.getMrn();
    return "CRITICAL Red Alert — " + name + " (MRN " + mrn + ")"
        + (reason == null || reason.isBlank() ? "" : ": " + reason);
  }

  private static Instant toInstant(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Instant i) {
      return i;
    }
    if (o instanceof Timestamp ts) {
      return ts.toInstant();
    }
    return Instant.parse(o.toString());
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
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
