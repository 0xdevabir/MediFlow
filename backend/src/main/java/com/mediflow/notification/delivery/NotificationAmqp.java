package com.mediflow.notification.delivery;

import com.mediflow.notification.domain.Channel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for notifications. Declared by {@code RabbitAdmin} when RabbitMQ is configured;
 * an inert bean otherwise.
 *
 * <pre>
 *   notify (direct) ── notify.push | notify.sms | notify.email | notify.in_app   (work queues)
 *                   └─ notify.dlq                                                (final failures)
 *   notify.retry (fanout) ── notify.retry (no consumer; per-message TTL = backoff,
 *                                          dead-letters back to "notify" with the original key)
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
public class NotificationAmqp {

  public static final String EXCHANGE = "notify";
  public static final String RETRY_EXCHANGE = "notify.retry";
  public static final String RETRY_QUEUE = "notify.retry";
  public static final String DLQ = "notify.dlq";

  public static List<String> workQueues() {
    return Channel.ESCALATION_ORDER.stream().map(Channel::routingKey).toList();
  }

  @Bean
  Declarables notificationTopology() {
    DirectExchange exchange = new DirectExchange(EXCHANGE, true, false);
    FanoutExchange retryExchange = new FanoutExchange(RETRY_EXCHANGE, true, false);
    Queue retryQueue =
        QueueBuilder.durable(RETRY_QUEUE).withArguments(Map.of("x-dead-letter-exchange", EXCHANGE)).build();
    Queue dlq = QueueBuilder.durable(DLQ).build();
    List<Declarable> all = new ArrayList<>(List.of(exchange, retryExchange, retryQueue, dlq));
    all.add(BindingBuilder.bind(retryQueue).to(retryExchange));
    all.add(BindingBuilder.bind(dlq).to(exchange).with(DLQ));
    for (String name : workQueues()) {
      Queue q = QueueBuilder.durable(name).build();
      Binding b = BindingBuilder.bind(q).to(exchange).with(name);
      all.add(q);
      all.add(b);
    }
    return new Declarables(all);
  }
}
