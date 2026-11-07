package com.mediflow.notification.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.config.NotificationProperties;
import com.mediflow.notification.domain.Channel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Hands notifications to the delivery pipeline.
 *
 * <ul>
 *   <li><b>rabbit</b> (a RabbitMQ {@link ConnectionFactory} exists): publish to {@code notify.<channel>};
 *       a listener container consumes the work queues; retries are re-published to {@code
 *       notify.retry} with a per-message TTL (exponential backoff) that dead-letters back.
 *   <li><b>in-memory</b> (no RabbitMQ, e.g. the test profile): first attempt runs synchronously
 *       after commit; retries are scheduled on a small in-process executor (zero delay = inline).
 * </ul>
 *
 * Dispatch always happens after the surrounding transaction commits, so consumers never see an
 * uncommitted row.
 */
@Component
public class NotificationDispatcher implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

  private final DeliveryService delivery;
  private final NotificationProperties props;
  private final ObjectMapper json;
  private final RabbitTemplate rabbit;
  private final ConnectionFactory connectionFactory;
  private final ScheduledExecutorService retryExecutor;
  private SimpleMessageListenerContainer container;
  private volatile boolean running;

  public NotificationDispatcher(
      DeliveryService delivery,
      NotificationProperties props,
      ObjectMapper json,
      ObjectProvider<RabbitTemplate> rabbit,
      ObjectProvider<ConnectionFactory> connectionFactory) {
    this.delivery = delivery;
    this.props = props;
    this.json = json;
    boolean wantRabbit = !"sync".equalsIgnoreCase(props.transport());
    this.rabbit = wantRabbit ? rabbit.getIfAvailable() : null;
    this.connectionFactory = wantRabbit ? connectionFactory.getIfAvailable() : null;
    if ("rabbit".equalsIgnoreCase(props.transport()) && (this.rabbit == null || this.connectionFactory == null)) {
      throw new IllegalStateException("mediflow.notification.transport=rabbit but RabbitMQ is not configured");
    }
    this.retryExecutor =
        usesRabbit()
            ? null
            : Executors.newScheduledThreadPool(
                2,
                r -> {
                  Thread t = new Thread(r, "notify-retry");
                  t.setDaemon(true);
                  return t;
                });
    log.info("Notification transport: {}", usesRabbit() ? "rabbitmq" : "in-memory");
  }

  public boolean usesRabbit() {
    return rabbit != null && connectionFactory != null;
  }

  /** Dispatch a freshly persisted notification once the current transaction commits. */
  public void dispatchAfterCommit(UUID notificationId, Channel channel) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              dispatch(notificationId, channel, 1, Duration.ZERO);
            }
          });
    } else {
      dispatch(notificationId, channel, 1, Duration.ZERO);
    }
  }

  void dispatch(UUID notificationId, Channel channel, int attempt, Duration delay) {
    if (usesRabbit()) {
      publish(notificationId, channel, attempt, delay);
    } else if (delay.isZero() || delay.isNegative()) {
      handle(notificationId, channel, attempt);
    } else {
      retryExecutor.schedule(
          () -> handle(notificationId, channel, attempt), delay.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  /** Runs one attempt and schedules whatever follows (retry or escalations). Never throws. */
  void handle(UUID notificationId, Channel channel, int attempt) {
    try {
      DeliveryOutcome outcome = delivery.attempt(notificationId, attempt);
      switch (outcome.kind()) {
        case RETRY -> dispatch(notificationId, channel, attempt + 1, outcome.retryAfter());
        case FAILED -> {
          if (usesRabbit()) {
            rabbit.send(NotificationAmqp.EXCHANGE, NotificationAmqp.DLQ, toMessage(notificationId, attempt, null));
          }
          outcome.escalations().forEach(e -> dispatch(e.notificationId(), e.channel(), 1, Duration.ZERO));
        }
        default -> {}
      }
    } catch (RuntimeException e) {
      log.error("Notification {} delivery crashed on attempt {}", notificationId, attempt, e);
    }
  }

  private void publish(UUID notificationId, Channel channel, int attempt, Duration delay) {
    try {
      if (delay.isZero() || delay.isNegative()) {
        rabbit.send(NotificationAmqp.EXCHANGE, channel.routingKey(), toMessage(notificationId, attempt, null));
      } else {
        // Published to the retry exchange with the work-queue routing key; the TTL dead-letters it
        // back to "notify" → notify.<channel>.
        rabbit.send(
            NotificationAmqp.RETRY_EXCHANGE,
            channel.routingKey(),
            toMessage(notificationId, attempt, Long.toString(delay.toMillis())));
      }
    } catch (RuntimeException e) {
      // Broker down: degrade to in-process delivery rather than lose the notification.
      log.warn("RabbitMQ publish failed for notification {}, delivering in-process", notificationId, e);
      handle(notificationId, channel, attempt);
    }
  }

  private Message toMessage(UUID notificationId, int attempt, String expirationMs) {
    try {
      byte[] body =
          json.writeValueAsBytes(Map.of("notificationId", notificationId.toString(), "attempt", attempt));
      var builder =
          MessageBuilder.withBody(body)
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .setContentEncoding(StandardCharsets.UTF_8.name());
      if (expirationMs != null) {
        builder.setExpiration(expirationMs);
      }
      return builder.build();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot serialise notification message", e);
    }
  }

  private void onMessage(Message message) {
    try {
      Map<?, ?> body = json.readValue(message.getBody(), Map.class);
      UUID id = UUID.fromString(String.valueOf(body.get("notificationId")));
      int attempt = ((Number) body.get("attempt")).intValue();
      String key = message.getMessageProperties().getReceivedRoutingKey();
      Channel channel =
          Channel.ESCALATION_ORDER.stream().filter(c -> c.routingKey().equals(key)).findFirst().orElse(Channel.IN_APP);
      handle(id, channel, attempt);
    } catch (Exception e) {
      log.error("Unreadable notification message dropped", e);
    }
  }

  @Override
  public void start() {
    if (usesRabbit()) {
      container = new SimpleMessageListenerContainer(connectionFactory);
      container.setQueueNames(NotificationAmqp.workQueues().toArray(String[]::new));
      container.setAcknowledgeMode(AcknowledgeMode.AUTO);
      container.setDefaultRequeueRejected(false);
      container.setConcurrentConsumers(2);
      container.setMessageListener(this::onMessage);
      container.start();
    }
    running = true;
  }

  @Override
  public void stop() {
    if (container != null) {
      container.stop();
    }
    if (retryExecutor != null) {
      retryExecutor.shutdown();
    }
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
