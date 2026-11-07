package com.mediflow.pharmacy.dispensing;

/** Domain rule failure surfaced to controllers as a flash {@code errorKey} or message. */
public class PharmacyException extends RuntimeException {

  private final String messageKey;

  public PharmacyException(String messageKey) {
    super(messageKey);
    this.messageKey = messageKey;
  }

  public PharmacyException(String messageKey, String detail) {
    super(detail);
    this.messageKey = messageKey;
  }

  public String messageKey() {
    return messageKey;
  }
}
