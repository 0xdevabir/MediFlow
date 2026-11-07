package com.mediflow.prescription.support;

/** Domain exceptions for the prescription module (mapped in {@code RxWebAdvice}). */
public final class RxExceptions {

  private RxExceptions() {}

  public static class RxException extends RuntimeException {
    private final String messageKey;

    public RxException(String messageKey, String detail) {
      super(detail);
      this.messageKey = messageKey;
    }

    public String messageKey() {
      return messageKey;
    }
  }

  public static class NotDraftException extends RxException {
    public NotDraftException() {
      super("rx.error.not_draft", "Prescription is not a draft");
    }
  }

  public static class SignBlockedException extends RxException {
    public SignBlockedException(String detail) {
      super("rx.error.sign_blocked", detail);
    }
  }

  public static class LicenceRequiredException extends RxException {
    public LicenceRequiredException() {
      super("rx.error.licence_required", "Practitioner licence number required to sign");
    }
  }

  public static class MfaStepUpRequiredException extends RxException {
    public MfaStepUpRequiredException() {
      super("rx.error.mfa_step_up", "Fresh MFA required");
    }
  }

  public static class AiUnavailableException extends RxException {
    public AiUnavailableException(String detail) {
      super("rx.error.ai_unavailable", detail);
    }
  }

  public static class ValidationException extends RxException {
    public ValidationException(String messageKey, String detail) {
      super(messageKey, detail);
    }
  }
}
