package com.mediflow.patient.service;

/** A business rule was violated; {@link #messageKey()} is a patient_* message key for the UI. */
public class PatientRuleException extends RuntimeException {

  private final String messageKey;

  public PatientRuleException(String messageKey) {
    super(messageKey);
    this.messageKey = messageKey;
  }

  public String messageKey() {
    return messageKey;
  }
}
