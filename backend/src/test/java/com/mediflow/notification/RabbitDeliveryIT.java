package com.mediflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.notification.delivery.NotificationDispatcher;
import com.mediflow.notification.domain.Channel;
import com.mediflow.notification.domain.NotificationEventType;
import com.mediflow.notification.service.NotificationService;
import com.mediflow.notification.service.NotificationService.Request;
import com.mediflow.shared.domain.Notification;
import com.mediflow.shared.domain.NotificationStatus;
import com.mediflow.shared.domain.Patient;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Same pipeline over a real RabbitMQ broker: work queues {@code notify.*}, TTL retry queue with
 * exponential backoff, escalation to the next channel after the final failure.
 */
@TestPropertySource(
    properties = {
      // re-enable RabbitAutoConfiguration (the test profile excludes it)
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration",
      "mediflow.notification.transport=rabbit",
      "mediflow.notification.initial-backoff=200ms",
      "mediflow.notification.max-backoff=1s"
    })
class RabbitDeliveryIT extends NotificationTestSupport {

  static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

  static {
    RABBIT.start();
  }

  @DynamicPropertySource
  static void rabbitProps(DynamicPropertyRegistry registry) {
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
  }

  @Autowired NotificationService service;
  @Autowired NotificationDispatcher dispatcher;

  @Test
  void usesTheBroker() {
    assertThat(dispatcher.usesRabbit()).isTrue();
  }

  @Test
  void transientFailureIsRetriedThroughTheRetryQueue() {
    Patient p = newPatient(null, "01733000001", null);
    sms.transientFailures.set(2);

    tx.executeWithoutResult(s -> service.send(
        Request.toPatient(NotificationEventType.RX_SIGNED, p.getId(), "Rahim").withChannels(EnumSet.of(Channel.SMS))));

    Notification n = forPatient(p.getId()).get(0);
    await(() -> reload(n).getStatus() == NotificationStatus.SENT);
    assertThat(meta(n).getAttempts()).isEqualTo(3);
    assertThat(sms.calls.get()).isEqualTo(3);
  }

  @Test
  void finalFailureEscalatesToEmail() {
    Patient p = newPatient(null, "01733000002", "rabbit@example.com");
    sms.alwaysFail = true;

    tx.executeWithoutResult(s -> service.send(
        Request.toPatient(NotificationEventType.RX_SIGNED, p.getId(), "Rahim").withChannels(EnumSet.of(Channel.SMS))));

    await(() -> forPatient(p.getId()).stream()
        .anyMatch(n -> n.getChannel().equals("EMAIL") && reload(n).getStatus() == NotificationStatus.SENT));
    List<Notification> chain = forPatient(p.getId());
    Notification smsRow = chain.stream().filter(n -> n.getChannel().equals("SMS")).findFirst().orElseThrow();
    assertThat(reload(smsRow).getStatus()).isEqualTo(NotificationStatus.FAILED);
    assertThat(meta(smsRow).getAttempts()).isEqualTo(3);
  }

  private static void await(BooleanSupplier condition) {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
    while (!condition.getAsBoolean()) {
      if (Instant.now().isAfter(deadline)) {
        throw new AssertionError("condition not met within 30s");
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
  }
}
