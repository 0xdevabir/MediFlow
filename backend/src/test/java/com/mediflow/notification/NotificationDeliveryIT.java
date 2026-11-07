package com.mediflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.notification.domain.Channel;
import com.mediflow.notification.domain.NotificationEventType;
import com.mediflow.notification.domain.Priority;
import com.mediflow.notification.domain.StaffCallEntry;
import com.mediflow.notification.domain.StaffCallStatus;
import com.mediflow.notification.repo.StaffCallEntryRepository;
import com.mediflow.notification.service.NotificationService;
import com.mediflow.notification.service.NotificationService.Request;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Notification;
import com.mediflow.shared.domain.NotificationStatus;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.NotificationPort;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;

/** Event → template / locale / recipients, and retry + escalation + staff call list (risk R7). */
class NotificationDeliveryIT extends NotificationTestSupport {

  @Autowired ApplicationEventPublisher events;
  @Autowired NotificationService service;
  @Autowired NotificationPort port;
  @Autowired MessageSource messages;
  @Autowired StaffCallEntryRepository callList;

  private void publish(Object event) {
    // Publish inside a committed transaction, as the owning modules do.
    tx.executeWithoutResult(s -> events.publishEvent(event));
  }

  private String name(Patient p) {
    return p.getGivenName() + " " + p.getFamilyName();
  }

  private String text(String key, Locale loc, Object... args) {
    return messages.getMessage(key, args, loc);
  }

  @Test
  void portImplementationReplacesTheT00Stub() {
    assertThat(port).isInstanceOf(NotificationService.class);
  }

  @Test
  void reportReadyUsesPatientsPreferredBanglaOnEveryReachableChannel() {
    AppUser u = newUser("en", Role.ROLE_PATIENT);
    preferLocale(u.getId(), "bn"); // preference wins over app_user.locale
    Patient p = newPatient(u.getId(), "01711000001", "rahim@example.com");

    publish(new DomainEvents.LabReportVerified(UUID.randomUUID(), UUID.randomUUID(), p.getId(), false, false, Instant.now()));

    List<Notification> sent = forPatient(p.getId());
    assertThat(sent).extracting(Notification::getChannel)
        .containsExactlyInAnyOrder("SMS", "EMAIL", "IN_APP");
    Locale bn = Locale.forLanguageTag("bn");
    assertThat(sent).allSatisfy(n -> {
      assertThat(n.getLocale()).isEqualTo("bn");
      assertThat(n.getSubject()).isEqualTo(text("notification.tpl.REPORT_READY.subject", bn, name(p)));
      assertThat(n.getBody()).isEqualTo(text("notification.tpl.REPORT_READY.body", bn, name(p)));
      assertThat(n.getRecipientUserId()).isEqualTo(u.getId());
      assertThat(reload(n).getStatus()).isEqualTo(NotificationStatus.SENT);
      assertThat(meta(n).getEventType()).isEqualTo(NotificationEventType.REPORT_READY);
    });
    assertThat(sms.sent).singleElement().asString().startsWith("01711000001|");
    assertThat(text("notification.tpl.REPORT_READY.subject", bn, "x"))
        .isNotEqualTo(text("notification.tpl.REPORT_READY.subject", Locale.ENGLISH, "x"));
  }

  @Test
  void patientWithoutAccountGetsEnglishSmsAndEmailOnly() {
    Patient p = newPatient(null, "01711000002", "karim@example.com");

    publish(new DomainEvents.LabReportVerified(UUID.randomUUID(), UUID.randomUUID(), p.getId(), true, false, Instant.now()));

    List<Notification> sent = forPatient(p.getId());
    assertThat(sent).extracting(Notification::getChannel).containsExactlyInAnyOrder("SMS", "EMAIL");
    assertThat(sent).allSatisfy(n -> {
      assertThat(n.getLocale()).isEqualTo("en");
      assertThat(n.getSubject()).isEqualTo(text("notification.tpl.SEE_DOCTOR.subject", Locale.ENGLISH, name(p)));
    });
  }

  @Test
  void criticalLabResultIsHighPriorityAndAlwaysPhonedThrough() {
    Patient p = newPatient(null, "01711000003", null);

    publish(new DomainEvents.LabReportVerified(UUID.randomUUID(), UUID.randomUUID(), p.getId(), true, true, Instant.now()));

    List<Notification> sent = forPatient(p.getId());
    assertThat(sent).extracting(Notification::getChannel).containsExactly("SMS");
    assertThat(meta(sent.get(0)).getPriority()).isEqualTo(Priority.HIGH);
    assertThat(meta(sent.get(0)).getEventType()).isEqualTo(NotificationEventType.LAB_CRITICAL_PATIENT);
    assertThat(openCallsFor(p.getId()))
        .anySatisfy(c -> {
          assertThat(c.getPriority()).isEqualTo(Priority.HIGH);
          assertThat(c.getEventType()).isEqualTo(NotificationEventType.LAB_CRITICAL_PATIENT);
        });
  }

  @Test
  void redAlertReachesErAndNurseUsersInTheirLanguage() {
    Patient p = newPatient(null, null, null);
    UUID er = users.findByUsername("er").orElseThrow().getId();
    UUID nurse = users.findByUsername("nurse").orElseThrow().getId();
    AppUser bnNurse = newUser("bn", Role.ROLE_NURSE);

    publish(new DomainEvents.RedAlertRaised(UUID.randomUUID(), p.getId(), UUID.randomUUID(), TriageLevel.CRITICAL, Instant.now()));

    List<Notification> alerts = notificationRows.findAll().stream()
        .filter(n -> n.getBody() != null && n.getBody().contains(p.getFamilyName()))
        .toList();
    assertThat(alerts).isNotEmpty().allSatisfy(n -> {
      assertThat(n.getChannel()).isEqualTo("IN_APP");
      assertThat(meta(n).getPriority()).isEqualTo(Priority.HIGH);
      assertThat(meta(n).getEventType()).isEqualTo(NotificationEventType.RED_ALERT);
    });
    Set<UUID> recipients = alerts.stream().map(Notification::getRecipientUserId).collect(Collectors.toSet());
    assertThat(recipients).contains(er, nurse, bnNurse.getId());
    assertThat(users.findByUsername("doctor").map(AppUser::getId)).isPresent().get().isNotIn(recipients);
    assertThat(alerts).filteredOn(n -> n.getRecipientUserId().equals(bnNurse.getId()))
        .singleElement().extracting(Notification::getLocale).isEqualTo("bn");
  }

  @Test
  void transientSmsFailureIsRetriedThenSent() {
    Patient p = newPatient(null, "01711000004", null);
    sms.transientFailures.set(2);

    tx.executeWithoutResult(s -> service.send(
        Request.toPatient(NotificationEventType.RX_SIGNED, p.getId(), name(p)).withChannels(EnumSet.of(Channel.SMS))));

    Notification n = forPatient(p.getId()).get(0);
    assertThat(reload(n).getStatus()).isEqualTo(NotificationStatus.SENT);
    assertThat(meta(n).getAttempts()).isEqualTo(3);
    assertThat(sms.calls.get()).isEqualTo(3);
    assertThat(openCallsFor(p.getId())).isEmpty();
  }

  @Test
  void finalSmsFailureEscalatesToEmailAndAddsStaffCall() {
    Patient p = newPatient(null, "01711000005", "esc@example.com");
    sms.alwaysFail = true;

    tx.executeWithoutResult(s -> service.send(
        Request.toPatient(NotificationEventType.RX_SIGNED, p.getId(), name(p)).withChannels(EnumSet.of(Channel.SMS))));

    List<Notification> chain = forPatient(p.getId());
    Notification smsRow = chain.stream().filter(n -> n.getChannel().equals("SMS")).findFirst().orElseThrow();
    Notification emailRow = chain.stream().filter(n -> n.getChannel().equals("EMAIL")).findFirst().orElseThrow();
    assertThat(reload(smsRow).getStatus()).isEqualTo(NotificationStatus.FAILED);
    assertThat(meta(smsRow).getAttempts()).isEqualTo(3);
    assertThat(meta(smsRow).getEscalatedToId()).isEqualTo(emailRow.getId());
    assertThat(meta(emailRow).getRootNotificationId()).isEqualTo(smsRow.getId());
    assertThat(reload(emailRow).getStatus()).isEqualTo(NotificationStatus.SENT);
    assertThat(sms.calls.get()).isEqualTo(3);
    assertThat(openCallsFor(p.getId())).singleElement().satisfies(c -> {
      assertThat(c.getRootNotificationId()).isEqualTo(smsRow.getId());
      assertThat(c.getReason()).contains("SMS failed after 3");
    });
  }

  @Test
  void permanentFailureSkipsRetriesAndWithNoOtherChannelLandsOnCallList() {
    Patient p = newPatient(null, "01711000006", null);
    sms.alwaysFail = true;
    sms.permanent = true;

    tx.executeWithoutResult(s -> service.send(
        Request.toPatient(NotificationEventType.RX_SIGNED, p.getId(), name(p))));

    List<Notification> chain = forPatient(p.getId());
    assertThat(chain).singleElement().satisfies(n -> {
      assertThat(reload(n).getStatus()).isEqualTo(NotificationStatus.FAILED);
      assertThat(meta(n).getAttempts()).isEqualTo(1);
      assertThat(meta(n).getEscalatedToId()).isNull();
    });
    assertThat(openCallsFor(p.getId())).hasSize(1);
  }

  @Test
  void unreachablePatientGoesStraightToCallList() {
    Patient p = newPatient(null, null, null);

    publish(new DomainEvents.MedicationDispensed(UUID.randomUUID(), UUID.randomUUID(), p.getId(), Instant.now()));

    assertThat(forPatient(p.getId())).isEmpty();
    assertThat(openCallsFor(p.getId())).singleElement()
        .extracting(StaffCallEntry::getEventType).isEqualTo(NotificationEventType.MEDICATION_DISPENSED);
  }

  @Test
  void quietHoursKeepNormalMessagesInAppButNotHighPriority() {
    AppUser u = newUser("en", Role.ROLE_PATIENT);
    var pref = com.mediflow.notification.domain.NotificationPreference.defaultsFor(u.getId());
    pref.setQuietStart(java.time.LocalTime.MIDNIGHT);
    pref.setQuietEnd(java.time.LocalTime.of(23, 59, 59)); // always quiet
    preferences.save(pref);
    Patient p = newPatient(u.getId(), "01711000007", null);

    tx.executeWithoutResult(s -> service.send(Request.toPatient(NotificationEventType.RX_SIGNED, p.getId(), name(p))));
    assertThat(forPatient(p.getId())).extracting(Notification::getChannel).containsExactly("IN_APP");

    tx.executeWithoutResult(s -> service.send(
        Request.toPatient(NotificationEventType.RX_REVOKED, p.getId(), name(p)).withPriority(Priority.HIGH)));
    assertThat(forPatient(p.getId())).extracting(Notification::getChannel).contains("SMS");
  }

  @Test
  void directPortCallHonoursExplicitChannelAndLocale() {
    AppUser u = newUser("en", Role.ROLE_DOCTOR);
    tx.executeWithoutResult(s -> port.notifyUser(u.getId(), "IN_APP", "bn-BD", "Hello", "Body"));

    assertThat(inApp.findInbox(u.getId(), org.springframework.data.domain.PageRequest.of(0, 5)))
        .singleElement().satisfies(n -> {
          assertThat(n.getLocale()).isEqualTo("bn");
          assertThat(n.getSubject()).isEqualTo("Hello");
          assertThat(meta(n).getEventType()).isEqualTo(NotificationEventType.DIRECT);
        });
  }

  private List<StaffCallEntry> openCallsFor(UUID patientId) {
    return callList.findAll().stream()
        .filter(c -> patientId.equals(c.getPatientId()) && c.getStatus() == StaffCallStatus.OPEN)
        .toList();
  }
}
