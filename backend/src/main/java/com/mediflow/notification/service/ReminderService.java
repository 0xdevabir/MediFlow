package com.mediflow.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.config.NotificationProperties;
import com.mediflow.notification.domain.AdherenceLog;
import com.mediflow.notification.domain.AdherenceOutcome;
import com.mediflow.notification.domain.NotificationEventType;
import com.mediflow.notification.repo.AdherenceLogRepository;
import com.mediflow.notification.repo.PrescriptionLookupRepository;
import com.mediflow.notification.repo.ReminderLookupRepository;
import com.mediflow.shared.domain.PrescriptionItem;
import com.mediflow.shared.domain.Reminder;
import com.mediflow.shared.domain.ReminderStatus;
import com.mediflow.shared.port.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reminder scheduler (M8): creates medicine reminders from signed prescriptions, visit reminders
 * from appointments / follow-up dates, sends due reminders and records patient acknowledgements in
 * the adherence log. Reminders live in the T00 {@code reminder} table; {@code payload.sourceId}
 * links them to the prescription / appointment they came from.
 */
@Service
public class ReminderService {

  public static final String MEDICATION = "MEDICATION";
  public static final String APPOINTMENT_24H = "APPOINTMENT_24H";
  public static final String APPOINTMENT_2H = "APPOINTMENT_2H";
  public static final String FOLLOW_UP = "FOLLOW_UP";
  /** {@code reminder.channel}: delivery follows the patient's preferences at send time. */
  public static final String PREFERRED_CHANNEL = "PREFERRED";
  /** A dose may be acknowledged up to one hour before its scheduled time. */
  public static final Duration EARLY_ACK_WINDOW = Duration.ofHours(1);

  private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

  private final ReminderLookupRepository reminders;
  private final com.mediflow.shared.repo.ReminderRepository reminderStore;
  private final PrescriptionLookupRepository rx;
  private final AdherenceLogRepository adherence;
  private final NotificationService notifications;
  private final TemplateRenderer templates;
  private final RecipientResolver recipients;
  private final NotificationProperties props;
  private final ClockPort clock;
  private final ObjectMapper json;

  public ReminderService(
      ReminderLookupRepository reminders,
      com.mediflow.shared.repo.ReminderRepository reminderStore,
      PrescriptionLookupRepository rx,
      AdherenceLogRepository adherence,
      NotificationService notifications,
      TemplateRenderer templates,
      RecipientResolver recipients,
      NotificationProperties props,
      ClockPort clock,
      ObjectMapper json) {
    this.reminders = reminders;
    this.reminderStore = reminderStore;
    this.rx = rx;
    this.adherence = adherence;
    this.notifications = notifications;
    this.templates = templates;
    this.recipients = recipients;
    this.props = props;
    this.clock = clock;
    this.json = json;
  }

  // ---------------------------------------------------------------- scheduling

  /**
   * Medicine reminders for every item of a signed prescription (dose times by frequency for the
   * duration, starting after {@code from}). Replaces pending reminders of the same prescription.
   */
  @Transactional
  public List<Reminder> scheduleForPrescription(UUID prescriptionId, UUID patientId, Instant from) {
    reminders.cancelPendingForSource(prescriptionId.toString());
    List<Reminder> created = new ArrayList<>();
    for (PrescriptionItem item : rx.findByPrescriptionId(prescriptionId)) {
      List<Instant> doses =
          DoseScheduleCalculator.schedule(item.getFrequency(), item.getDuration(), from, props.zone());
      if (doses.isEmpty()) {
        log.info(
            "No reminders for Rx item {} (frequency/duration not schedulable)", item.getId());
        continue;
      }
      String drug = drugName(item);
      for (Instant at : doses) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceId", prescriptionId.toString());
        payload.put("itemId", item.getId().toString());
        payload.put("drug", drug);
        payload.put("dose", item.getDose() == null ? "" : item.getDose());
        created.add(newReminder(patientId, MEDICATION, at, payload));
      }
    }
    return reminderStore.saveAll(created);
  }

  /** Visit reminders 24 h and 2 h before the appointment (only those still in the future). */
  @Transactional
  public List<Reminder> scheduleForAppointment(UUID appointmentId, UUID patientId, Instant startsAt) {
    reminders.cancelPendingForSource(appointmentId.toString());
    Instant now = clock.now();
    List<Reminder> created = new ArrayList<>();
    for (var entry : Map.of(APPOINTMENT_24H, Duration.ofHours(24), APPOINTMENT_2H, Duration.ofHours(2)).entrySet()) {
      Instant at = startsAt.minus(entry.getValue());
      if (at.isAfter(now)) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceId", appointmentId.toString());
        payload.put("startsAt", startsAt.toString());
        created.add(newReminder(patientId, entry.getKey(), at, payload));
      }
    }
    return reminderStore.saveAll(created);
  }

  /**
   * Follow-up visit reminders: the day before at 18:00 and on the day at 08:00 (local zone). Driven
   * by {@link com.mediflow.shared.event.DomainEvents.FollowUpScheduled} (clinical / Rx publishers).
   */
  @Transactional
  public List<Reminder> scheduleFollowUp(UUID sourceId, UUID patientId, LocalDate date) {
    reminders.cancelPendingForSource(sourceId.toString());
    Instant now = clock.now();
    List<Reminder> created = new ArrayList<>();
    for (Instant at :
        List.of(
            date.minusDays(1).atTime(LocalTime.of(18, 0)).atZone(props.zone()).toInstant(),
            date.atTime(LocalTime.of(8, 0)).atZone(props.zone()).toInstant())) {
      if (at.isAfter(now)) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceId", sourceId.toString());
        payload.put("date", date.toString());
        created.add(newReminder(patientId, FOLLOW_UP, at, payload));
      }
    }
    return reminderStore.saveAll(created);
  }

  @Transactional
  public int cancelForSource(UUID sourceId) {
    return reminders.cancelPendingForSource(sourceId.toString());
  }

  // ---------------------------------------------------------------- sending

  /** Sends every due PENDING reminder (locked with SKIP LOCKED, safe across instances). */
  @Transactional
  public int dispatchDue() {
    List<Reminder> due = reminders.lockDue(clock.now(), props.scheduler().batchSize());
    for (Reminder r : due) {
      try {
        notifications.send(toRequest(r));
        r.setStatus(ReminderStatus.SENT);
      } catch (RuntimeException e) {
        log.warn("Reminder {} could not be sent", r.getId(), e);
        r.setStatus(ReminderStatus.FAILED);
      }
    }
    return due.size();
  }

  private NotificationService.Request toRequest(Reminder r) {
    Map<String, String> p = payload(r);
    String name = recipients.contact(null, r.getPatientId()).displayName();
    return switch (r.getReminderType()) {
      case MEDICATION ->
          NotificationService.Request.toPatient(
              NotificationEventType.MEDICATION_REMINDER,
              r.getPatientId(),
              name,
              p.getOrDefault("drug", ""),
              p.getOrDefault("dose", ""),
              r.getScheduledAt());
      case APPOINTMENT_24H, APPOINTMENT_2H ->
          NotificationService.Request.toPatient(
              APPOINTMENT_24H.equals(r.getReminderType())
                  ? NotificationEventType.APPOINTMENT_REMINDER_24H
                  : NotificationEventType.APPOINTMENT_REMINDER_2H,
              r.getPatientId(),
              name,
              Optional.ofNullable(p.get("startsAt")).map(Instant::parse).map(i -> (Object) i).orElse(""));
      case FOLLOW_UP ->
          NotificationService.Request.toPatient(
              NotificationEventType.FOLLOW_UP_REMINDER, r.getPatientId(), name, p.getOrDefault("date", ""));
      default -> throw new IllegalStateException("Unknown reminder type " + r.getReminderType());
    };
  }

  // ---------------------------------------------------------------- adherence

  /** Patient acknowledges a medicine reminder (TAKEN / SKIPPED); re-acknowledging overwrites. */
  @Transactional
  public AdherenceLog acknowledge(UUID reminderId, UUID patientId, AdherenceOutcome outcome) {
    Reminder r =
        reminderStore
            .findById(reminderId)
            .filter(x -> x.getPatientId().equals(patientId))
            .filter(x -> MEDICATION.equals(x.getReminderType()))
            .orElseThrow(() -> new IllegalArgumentException("Unknown reminder"));
    if (r.getStatus() == ReminderStatus.CANCELLED) {
      throw new IllegalArgumentException("Reminder cancelled");
    }
    Instant now = clock.now();
    if (r.getScheduledAt().isAfter(now.plus(EARLY_ACK_WINDOW))) {
      throw new IllegalArgumentException("Dose not due yet");
    }
    AdherenceLog entry =
        adherence
            .findByReminderId(reminderId)
            .orElseGet(() -> new AdherenceLog(reminderId, patientId, outcome, now));
    entry.setOutcome(outcome);
    entry.setAcknowledgedAt(now);
    return adherence.save(entry);
  }

  /** Adherence % = doses marked TAKEN / medicine doses already due (not cancelled); empty if none due. */
  @Transactional(readOnly = true)
  public Optional<Integer> adherencePercent(UUID patientId) {
    Instant now = clock.now();
    long due = reminders.countDue(patientId, MEDICATION, now, ReminderStatus.CANCELLED);
    if (due == 0) {
      return Optional.empty();
    }
    long taken =
        adherence.countForDue(patientId, AdherenceOutcome.TAKEN, now, ReminderStatus.CANCELLED);
    return Optional.of((int) Math.round(Math.min(taken, due) * 100.0 / due));
  }

  /** Row for the patient's reminder page. */
  public record ReminderRow(
      Reminder reminder, String drug, String dose, String when, AdherenceOutcome outcome, boolean due) {}

  @Transactional(readOnly = true)
  public List<ReminderRow> medicationRows(UUID patientId, String locale) {
    Instant now = clock.now();
    List<Reminder> list =
        reminders.findByPatientIdAndReminderTypeOrderByScheduledAtAsc(patientId, MEDICATION).stream()
            .filter(r -> r.getStatus() != ReminderStatus.CANCELLED)
            .toList();
    Map<UUID, AdherenceOutcome> outcomes = new java.util.HashMap<>();
    adherence
        .findByReminderIdIn(list.stream().map(Reminder::getId).toList())
        .forEach(a -> outcomes.put(a.getReminderId(), a.getOutcome()));
    java.util.Locale loc = java.util.Locale.forLanguageTag(locale);
    return list.stream()
        .map(
            r -> {
              Map<String, String> p = payload(r);
              return new ReminderRow(
                  r,
                  p.getOrDefault("drug", ""),
                  p.getOrDefault("dose", ""),
                  templates.formatInstant(r.getScheduledAt(), loc),
                  outcomes.get(r.getId()),
                  !r.getScheduledAt().isAfter(now));
            })
        .toList();
  }

  // ---------------------------------------------------------------- helpers

  private Reminder newReminder(UUID patientId, String type, Instant at, Map<String, Object> payload) {
    Reminder r = new Reminder();
    r.setPatientId(patientId);
    r.setReminderType(type);
    r.setChannel(PREFERRED_CHANNEL);
    r.setScheduledAt(at);
    r.setStatus(ReminderStatus.PENDING);
    try {
      r.setPayload(json.writeValueAsString(payload));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return r;
  }

  private Map<String, String> payload(Reminder r) {
    if (r.getPayload() == null) {
      return Map.of();
    }
    try {
      return json.readValue(r.getPayload(), new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }

  private String drugName(PrescriptionItem item) {
    if (item.getBrandId() != null) {
      List<String> brand = rx.findBrandName(item.getBrandId());
      if (!brand.isEmpty()) {
        return brand.get(0);
      }
    }
    List<String> generic = item.getGenericId() == null ? List.of() : rx.findGenericName(item.getGenericId());
    return generic.isEmpty() ? "" : generic.get(0);
  }
}
