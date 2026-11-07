package com.mediflow.identity.mfa;

/**
 * Thrown by {@link MfaReauthService#requireFreshMfa()} when the last MFA is older than 5 minutes.
 * Web requests are redirected to {@code /identity/mfa/step-up} by {@link MfaWebAdvice}.
 */
public class MfaStepUpRequiredException extends RuntimeException {

  public MfaStepUpRequiredException() {
    super("Fresh MFA required");
  }
}
