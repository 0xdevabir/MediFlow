package com.mediflow.notification.delivery;

import com.mediflow.notification.domain.Channel;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Result of one delivery attempt. */
public record DeliveryOutcome(Kind kind, Duration retryAfter, List<Escalation> escalations) {

  public enum Kind {
    DELIVERED,
    RETRY,
    FAILED,
    SKIPPED
  }

  /** A follow-up notification on the next channel that must be dispatched. */
  public record Escalation(UUID notificationId, Channel channel) {}

  public static DeliveryOutcome delivered() {
    return new DeliveryOutcome(Kind.DELIVERED, Duration.ZERO, List.of());
  }

  public static DeliveryOutcome skipped() {
    return new DeliveryOutcome(Kind.SKIPPED, Duration.ZERO, List.of());
  }

  public static DeliveryOutcome retry(Duration after) {
    return new DeliveryOutcome(Kind.RETRY, after, List.of());
  }

  public static DeliveryOutcome failed(List<Escalation> escalations) {
    return new DeliveryOutcome(Kind.FAILED, Duration.ZERO, List.copyOf(escalations));
  }
}
