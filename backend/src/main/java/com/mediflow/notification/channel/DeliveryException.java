package com.mediflow.notification.channel;

/**
 * Delivery failed. {@code permanent} failures (no address, rejected number) skip retries and
 * escalate to the next channel immediately; transient ones are retried with backoff.
 */
public class DeliveryException extends Exception {

  private final boolean permanent;

  public DeliveryException(String message, boolean permanent) {
    super(message);
    this.permanent = permanent;
  }

  public DeliveryException(String message, Throwable cause) {
    super(message, cause);
    this.permanent = false;
  }

  public static DeliveryException noAddress(String channel) {
    return new DeliveryException("No " + channel + " address for recipient", true);
  }

  public boolean isPermanent() {
    return permanent;
  }
}
