package com.mediflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.notification.domain.AdherenceOutcome;
import com.mediflow.notification.domain.NotificationEventType;
import com.mediflow.notification.repo.AdherenceLogRepository;
import com.mediflow.notification.repo.ReminderLookupRepository;
import com.mediflow.notification.service.ReminderService;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.DrugGeneric;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.EncounterStatus;
import com.mediflow.shared.domain.Hospital;
import com.mediflow.shared.domain.Notification;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionItem;
import com.mediflow.shared.domain.PrescriptionStatus;
import com.mediflow.shared.domain.Reminder;
import com.mediflow.shared.domain.ReminderStatus;
import com.mediflow.shared.domain.ReviewStatus;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.repo.DrugGenericRepository;
import com.mediflow.shared.repo.EncounterRepository;
import com.mediflow.shared.repo.HospitalRepository;
import com.mediflow.shared.repo.PrescriptionItemRepository;
import com.mediflow.shared.repo.PrescriptionRepository;
import com.mediflow.shared.repo.ReminderRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;

/** Reminder generation from a signed Rx, sending due reminders, acknowledgement → adherence, pages. */
class ReminderAdherenceIT extends NotificationTestSupport {

  @Autowired ApplicationEventPublisher events;
  @Autowired ReminderService reminders;
  @Autowired ReminderLookupRepository reminderLookup;
  @Autowired ReminderRepository reminderStore;
  @Autowired AdherenceLogRepository adherenceLog;
  @Autowired HospitalRepository hospitals;
  @Autowired EncounterRepository encounters;
  @Autowired PrescriptionRepository prescriptions;
  @Autowired PrescriptionItemRepository items;
  @Autowired DrugGenericRepository generics;
  @Autowired MockMvc mvc;

  // ------------------------------------------------------------------ schedule generation

  @Test
  void signedPrescriptionCreatesDoseRemindersForOdBdTdsTimesDuration() {
    Patient p = newPatient(null, "01722000001", null);
    UUID rx = prescription(p, new String[][] {
      {"OD", "5 days"}, {"BD", "7 days"}, {"TDS", "3 days"}, {"PRN", "5 days"}
    });

    tx.executeWithoutResult(s -> events.publishEvent(
        new DomainEvents.PrescriptionSigned(rx, p.getId(), null, Instant.now())));

    List<Reminder> created = pendingMedication(p.getId());
    assertThat(created).hasSize(5 + 14 + 9); // PRN produces none
    assertThat(created).allSatisfy(r -> {
      assertThat(r.getScheduledAt()).isAfter(Instant.now().minusSeconds(5));
      assertThat(r.getPayload()).contains("\"sourceId\":\"" + rx + "\"");
    });
    Map<String, Long> perDrug = created.stream()
        .collect(Collectors.groupingBy(r -> r.getPayload().replaceAll(".*\"drug\":\"([^\"]+)\".*", "$1"), Collectors.counting()));
    assertThat(perDrug.values()).containsExactlyInAnyOrder(5L, 14L, 9L);
    // the patient is told the Rx is ready too
    assertThat(forPatient(p.getId())).extracting(n -> meta(n).getEventType())
        .contains(NotificationEventType.RX_SIGNED);
  }

  @Test
  void amendmentReschedulesAndRevocationCancels() {
    Patient p = newPatient(null, "01722000002", null);
    UUID rx = prescription(p, new String[][] {{"BD", "2 days"}});
    tx.executeWithoutResult(s -> events.publishEvent(new DomainEvents.PrescriptionSigned(rx, p.getId(), null, Instant.now())));
    assertThat(pendingMedication(p.getId())).hasSize(4);

    tx.executeWithoutResult(s -> {
      PrescriptionItem i = items.findAll().stream().filter(x -> x.getPrescriptionId().equals(rx)).findFirst().orElseThrow();
      i.setFrequency("TDS");
      items.save(i);
    });
    tx.executeWithoutResult(s -> events.publishEvent(new DomainEvents.PrescriptionAmended(rx, p.getId(), Instant.now())));
    assertThat(pendingMedication(p.getId())).hasSize(6);

    tx.executeWithoutResult(s -> events.publishEvent(new DomainEvents.PrescriptionRevoked(rx, p.getId(), Instant.now())));
    assertThat(pendingMedication(p.getId())).isEmpty();
  }

  @Test
  void appointmentAndFollowUpRemindersAreScheduledAhead() {
    Patient p = newPatient(null, "01722000003", null);
    Instant start = Instant.now().plus(Duration.ofDays(3));
    tx.executeWithoutResult(s -> reminders.scheduleForAppointment(UUID.randomUUID(), p.getId(), start));
    tx.executeWithoutResult(s -> reminders.scheduleFollowUp(UUID.randomUUID(), p.getId(), LocalDate.now().plusDays(10)));

    List<Reminder> all = reminderLookup.findByPatientIdOrderByScheduledAtAsc(p.getId());
    assertThat(all).extracting(Reminder::getReminderType)
        .containsExactly(ReminderService.APPOINTMENT_24H, ReminderService.APPOINTMENT_2H,
            ReminderService.FOLLOW_UP, ReminderService.FOLLOW_UP);
    assertThat(all.get(0).getScheduledAt()).isEqualTo(start.minus(Duration.ofHours(24)));
    assertThat(all.get(1).getScheduledAt()).isEqualTo(start.minus(Duration.ofHours(2)));
  }

  @Test
  void followUpScheduledEventCreatesReminders() {
    Patient p = newPatient(null, "01722000013", null);
    UUID source = UUID.randomUUID();
    LocalDate date = LocalDate.now().plusDays(10);
    tx.executeWithoutResult(
        s -> events.publishEvent(new DomainEvents.FollowUpScheduled(source, p.getId(), date, Instant.now())));

    List<Reminder> all = reminderLookup.findByPatientIdOrderByScheduledAtAsc(p.getId());
    assertThat(all).extracting(Reminder::getReminderType)
        .containsExactly(ReminderService.FOLLOW_UP, ReminderService.FOLLOW_UP);
  }

  @Test
  void dueRemindersAreSentInThePatientsLanguage() {
    AppUser u = newUser("bn", Role.ROLE_PATIENT);
    Patient p = newPatient(u.getId(), "01722000004", null);
    Reminder r = reminder(p, Instant.now().minusSeconds(60));

    tx.execute(s -> reminders.dispatchDue());

    assertThat(reminderStore.findById(r.getId()).orElseThrow().getStatus()).isEqualTo(ReminderStatus.SENT);
    List<Notification> sent = forPatient(p.getId());
    assertThat(sent).isNotEmpty().allSatisfy(n -> {
      assertThat(meta(n).getEventType()).isEqualTo(NotificationEventType.MEDICATION_REMINDER);
      assertThat(n.getLocale()).isEqualTo("bn");
      assertThat(n.getBody()).contains("Napa");
    });
    assertThat(sms.sent).anySatisfy(s -> assertThat(s).startsWith("01722000004|"));
  }

  // ------------------------------------------------------------------ adherence

  @Test
  void acknowledgementUpdatesAdherencePercent() throws Exception {
    AppUser u = newUser("en", Role.ROLE_PATIENT);
    Patient p = newPatient(u.getId(), null, null);
    Instant now = Instant.now();
    Reminder r1 = reminder(p, now.minus(Duration.ofHours(30)));
    Reminder r2 = reminder(p, now.minus(Duration.ofHours(20)));
    Reminder r3 = reminder(p, now.minus(Duration.ofHours(10)));
    Reminder r4 = reminder(p, now.minus(Duration.ofHours(1)));
    Reminder future = reminder(p, now.plus(Duration.ofDays(2)));

    assertThat(reminders.adherencePercent(p.getId())).contains(0);
    reminders.acknowledge(r1.getId(), p.getId(), AdherenceOutcome.TAKEN);
    reminders.acknowledge(r2.getId(), p.getId(), AdherenceOutcome.SKIPPED);
    assertThat(reminders.adherencePercent(p.getId())).contains(25);

    // via the patient's own page
    mvc.perform(post("/notifications/reminders/{id}/ack", r3.getId()).param("outcome", "TAKEN")
            .with(user(u.getUsername()).roles("PATIENT")).with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/notifications/reminders"));
    assertThat(reminders.adherencePercent(p.getId())).contains(50);
    assertThat(adherenceLog.findByReminderId(r3.getId())).isPresent();

    // re-acknowledging overwrites
    reminders.acknowledge(r2.getId(), p.getId(), AdherenceOutcome.TAKEN);
    assertThat(reminders.adherencePercent(p.getId())).contains(75);

    mvc.perform(get("/notifications/reminders").with(user(u.getUsername()).roles("PATIENT")))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("75%")));

    // not yet due, someone else's, or unknown → rejected
    assertThatThrownBy(() -> reminders.acknowledge(future.getId(), p.getId(), AdherenceOutcome.TAKEN))
        .isInstanceOf(IllegalArgumentException.class);
    Patient other = newPatient(null, null, null);
    assertThatThrownBy(() -> reminders.acknowledge(r4.getId(), other.getId(), AdherenceOutcome.TAKEN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(reminders.adherencePercent(p.getId())).contains(75);
  }

  @Test
  void noDueDosesMeansNoAdherenceFigure() {
    Patient p = newPatient(null, null, null);
    reminder(p, Instant.now().plus(Duration.ofDays(1)));
    assertThat(reminders.adherencePercent(p.getId())).isEmpty();
  }

  // ------------------------------------------------------------------ pages and authorization

  @Test
  void pagesRenderAndAreRoleGuarded() throws Exception {
    mvc.perform(get("/notifications").with(user("doctor").roles("DOCTOR"))).andExpect(status().isOk());
    mvc.perform(get("/notifications/preferences").with(user("doctor").roles("DOCTOR"))).andExpect(status().isOk());
    mvc.perform(get("/notifications/call-list").with(user("nurse").roles("NURSE"))).andExpect(status().isOk());
    mvc.perform(get("/notifications/call-list").with(user("doctor").roles("DOCTOR"))).andExpect(status().isForbidden());
    mvc.perform(get("/notifications/reminders").with(user("doctor").roles("DOCTOR"))).andExpect(status().isForbidden());
    mvc.perform(get("/notifications")).andExpect(status().is3xxRedirection());
  }

  @Test
  void preferencesAreSavedAndInboxMarksRead() throws Exception {
    AppUser u = newUser("en", Role.ROLE_DOCTOR);
    mvc.perform(post("/notifications/preferences").with(user(u.getUsername()).roles("DOCTOR")).with(csrf())
            .param("sms", "true").param("inApp", "true").param("locale", "bn")
            .param("quietStart", "22:00").param("quietEnd", "07:00"))
        .andExpect(status().is3xxRedirection());
    var pref = preferences.findByUserId(u.getId()).orElseThrow();
    assertThat(pref.getLocale()).isEqualTo("bn");
    assertThat(pref.isEmailEnabled()).isFalse();
    assertThat(pref.isQuiet(java.time.LocalTime.of(23, 0))).isTrue();
    assertThat(pref.isQuiet(java.time.LocalTime.of(12, 0))).isFalse();

    tx.executeWithoutResult(s -> notificationsFor(u));
    Notification n = inApp.findInbox(u.getId(), org.springframework.data.domain.PageRequest.of(0, 5)).get(0);
    mvc.perform(get("/notifications").with(user(u.getUsername()).roles("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString(n.getSubject())));
    mvc.perform(post("/notifications/{id}/read", n.getId()).with(user(u.getUsername()).roles("DOCTOR")).with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertThat(meta(n).getReadAt()).isNotNull();
    assertThat(inApp.countUnread(u.getId())).isZero();

    // another user cannot mark it
    AppUser other = newUser("en", Role.ROLE_DOCTOR);
    assertThat(inbox.markRead(other.getId(), n.getId())).isFalse();
  }

  // ------------------------------------------------------------------ helpers

  @Autowired com.mediflow.notification.service.NotificationService notificationService;
  @Autowired com.mediflow.notification.service.NotificationInbox inbox;

  private void notificationsFor(AppUser u) {
    notificationService.send(com.mediflow.notification.service.NotificationService.Request
        .toUser(NotificationEventType.REPORT_READY_DOCTOR, u.getId(), "Rahim"));
  }

  private List<Reminder> pendingMedication(UUID patientId) {
    return reminderLookup
        .findByPatientIdAndReminderTypeOrderByScheduledAtAsc(patientId, ReminderService.MEDICATION).stream()
        .filter(r -> r.getStatus() == ReminderStatus.PENDING)
        .toList();
  }

  private Reminder reminder(Patient p, Instant at) {
    Reminder r = new Reminder();
    r.setPatientId(p.getId());
    r.setReminderType(ReminderService.MEDICATION);
    r.setChannel(ReminderService.PREFERRED_CHANNEL);
    r.setScheduledAt(at);
    r.setStatus(ReminderStatus.PENDING);
    r.setPayload("{\"sourceId\":\"" + UUID.randomUUID() + "\",\"drug\":\"Napa\",\"dose\":\"500 mg\"}");
    return reminderStore.save(r);
  }

  private UUID prescription(Patient p, String[][] lines) {
    return tx.execute(s -> {
      Hospital h = new Hospital();
      h.setName("T10 Hospital " + UUID.randomUUID());
      h.setActive(true);
      h = hospitals.save(h);
      Encounter e = new Encounter();
      e.setPatientId(p.getId());
      e.setHospitalId(h.getId());
      e.setStatus(EncounterStatus.IN_PROGRESS);
      e.setStartedAt(Instant.now());
      e = encounters.save(e);
      Prescription rx = new Prescription();
      rx.setEncounterId(e.getId());
      rx.setPatientId(p.getId());
      rx.setStatus(PrescriptionStatus.SIGNED);
      rx.setSignedAt(Instant.now());
      rx = prescriptions.save(rx);
      for (String[] line : lines) {
        DrugGeneric g = new DrugGeneric();
        g.setName("T10 generic " + UUID.randomUUID());
        g = generics.save(g);
        PrescriptionItem i = new PrescriptionItem();
        i.setPrescriptionId(rx.getId());
        i.setGenericId(g.getId());
        i.setDose("1 tab");
        i.setFrequency(line[0]);
        i.setDuration(line[1]);
        i.setReviewStatus(ReviewStatus.ACCEPTED);
        items.save(i);
      }
      return rx.getId();
    });
  }
}
