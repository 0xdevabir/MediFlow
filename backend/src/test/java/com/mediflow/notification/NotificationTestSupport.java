package com.mediflow.notification;

import com.mediflow.notification.domain.NotificationDelivery;
import com.mediflow.notification.domain.NotificationPreference;
import com.mediflow.notification.repo.InAppNotificationRepository;
import com.mediflow.notification.repo.NotificationDeliveryRepository;
import com.mediflow.notification.repo.NotificationPreferenceRepository;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Notification;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.repo.AppUserRepository;
import com.mediflow.shared.repo.NotificationRepository;
import com.mediflow.shared.repo.PatientRepository;
import com.mediflow.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared setup for the synchronous (no RabbitMQ) notification ITs: fake gateways, zero backoff so
 * retries run inline and deterministically, scheduler off (tests call {@code dispatchDue()}).
 */
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@Import(FakeGateways.class)
@TestPropertySource(
    properties = {
      "mediflow.notification.initial-backoff=0ms",
      "mediflow.notification.max-attempts=3",
      "mediflow.notification.scheduler.enabled=false"
    })
abstract class NotificationTestSupport extends AbstractIntegrationTest {

  @Autowired protected FakeGateways.FakeSms sms;
  @Autowired protected FakeGateways.FakePush push;
  @Autowired protected AppUserRepository users;
  @Autowired protected PatientRepository patients;
  @Autowired protected NotificationRepository notificationRows;
  @Autowired protected NotificationDeliveryRepository deliveries;
  @Autowired protected InAppNotificationRepository inApp;
  @Autowired protected NotificationPreferenceRepository preferences;
  @Autowired protected TransactionTemplate tx;

  @BeforeEach
  void resetGateways() {
    sms.reset();
    push.reset();
  }

  protected AppUser newUser(String locale, Role role) {
    AppUser u = new AppUser();
    u.setUsername("t10-" + UUID.randomUUID());
    u.setPasswordHash("{noop}x");
    u.setDisplayName("T10 user");
    u.setEnabled(true);
    u.setMfaEnabled(false);
    u.setLocale(locale);
    u.setRoles(Set.of(role));
    return users.save(u);
  }

  protected Patient newPatient(UUID userId, String phone, String email) {
    Patient p = new Patient();
    p.setUserId(userId);
    p.setGivenName("Rahim");
    p.setFamilyName("T10-" + UUID.randomUUID().toString().substring(0, 8));
    p.setPhone(phone);
    p.setEmail(email);
    return patients.save(p);
  }

  protected void preferLocale(UUID userId, String locale) {
    NotificationPreference p = NotificationPreference.defaultsFor(userId);
    p.setLocale(locale);
    preferences.save(p);
  }

  protected List<Notification> forPatient(UUID patientId) {
    return inApp.findByPatient(patientId, PageRequest.of(0, 200));
  }

  protected NotificationDelivery meta(Notification n) {
    return deliveries.findByNotificationId(n.getId()).orElseThrow();
  }

  protected Notification reload(Notification n) {
    return notificationRows.findById(n.getId()).orElseThrow();
  }
}
