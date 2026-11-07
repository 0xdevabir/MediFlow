package com.mediflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.notification.channel.Contact;
import com.mediflow.notification.domain.Channel;
import com.mediflow.notification.domain.NotificationPreference;
import com.mediflow.notification.service.NotificationService;
import com.mediflow.notification.service.RecipientResolver;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Docker-free checks of preference / reachability rules used by channel choice and escalation. */
class ChannelRulesTest {

  @Test
  void quietHoursHandleWindowsOverMidnight() {
    NotificationPreference p = NotificationPreference.defaultsFor(UUID.randomUUID());
    assertThat(p.isQuiet(LocalTime.of(23, 0))).isFalse();
    p.setQuietStart(LocalTime.of(22, 0));
    p.setQuietEnd(LocalTime.of(7, 0));
    assertThat(p.isQuiet(LocalTime.of(23, 0))).isTrue();
    assertThat(p.isQuiet(LocalTime.of(6, 59))).isTrue();
    assertThat(p.isQuiet(LocalTime.of(7, 0))).isFalse();
    assertThat(p.isQuiet(LocalTime.of(12, 0))).isFalse();
    p.setQuietStart(LocalTime.of(13, 0));
    p.setQuietEnd(LocalTime.of(14, 0));
    assertThat(p.isQuiet(LocalTime.of(13, 30))).isTrue();
    assertThat(p.isQuiet(LocalTime.of(14, 0))).isFalse();
  }

  @Test
  void defaultsAllowEveryChannel() {
    NotificationPreference p = NotificationPreference.defaultsFor(UUID.randomUUID());
    for (Channel c : Channel.values()) {
      assertThat(p.allows(c)).isTrue();
    }
  }

  @Test
  void reachabilityFollowsAvailableAddresses() {
    Contact patientOnly = new Contact(null, UUID.randomUUID(), "R", "0171", null, null);
    assertThat(NotificationService.reachable(patientOnly, Channel.SMS)).isTrue();
    assertThat(NotificationService.reachable(patientOnly, Channel.EMAIL)).isFalse();
    assertThat(NotificationService.reachable(patientOnly, Channel.IN_APP)).isFalse();
    assertThat(NotificationService.reachable(patientOnly, Channel.PUSH)).isFalse();
    Contact staff = new Contact(UUID.randomUUID(), null, "D", null, null, List.of("tok"));
    assertThat(NotificationService.reachable(staff, Channel.PUSH)).isTrue();
    assertThat(NotificationService.reachable(staff, Channel.IN_APP)).isTrue();
  }

  @Test
  void escalationOrderAndRoutingKeys() {
    assertThat(Channel.ESCALATION_ORDER).containsExactly(Channel.PUSH, Channel.SMS, Channel.EMAIL, Channel.IN_APP);
    assertThat(Channel.SMS.routingKey()).isEqualTo("notify.sms");
    assertThat(Channel.parse("in_app")).contains(Channel.IN_APP);
    assertThat(Channel.parse("fax")).isEmpty();
  }

  @Test
  void localesNormaliseToBanglaOrEnglish() {
    assertThat(RecipientResolver.normaliseLocale("bn-BD")).isEqualTo("bn");
    assertThat(RecipientResolver.normaliseLocale("EN_us")).isEqualTo("en");
    assertThat(RecipientResolver.normaliseLocale(null)).isEqualTo("en");
    assertThat(RecipientResolver.normaliseLocale("fr")).isEqualTo("en");
  }
}
