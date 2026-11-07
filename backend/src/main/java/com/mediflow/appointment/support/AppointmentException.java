package com.mediflow.appointment.support;

/**
 * Business-rule failure in M2. {@link #messageKey()} is a message key from {@code
 * messages/appointment_*} so controllers can show it as a flash ({@code errorKey}).
 */
public class AppointmentException extends RuntimeException {

  private final String messageKey;

  public AppointmentException(String messageKey) {
    super(messageKey);
    this.messageKey = messageKey;
  }

  public String messageKey() {
    return messageKey;
  }
}
