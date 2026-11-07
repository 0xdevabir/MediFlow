package com.mediflow.lab.service;

/**
 * A lab business rule was violated (e.g. a non-direct test booked directly). Carries a message key
 * from {@code messages/lab_*} so controllers can show it as a flash error.
 */
public class LabRuleException extends RuntimeException {

  private final String messageKey;

  public LabRuleException(String messageKey) {
    super(messageKey);
    this.messageKey = messageKey;
  }

  public String getMessageKey() {
    return messageKey;
  }
}
