package com.mediflow.prescription.sign;

/**
 * MFA step-up for e-sign. T01 owns {@code MfaReauthService}; when that bean is present it should be
 * preferred. This module ships a session-attribute fallback that understands the same keys T01
 * documents ({@code identity.mfa.verifiedAt} / {@code identity.mfa.verifiedFor}, 5-minute window).
 */
public interface RxMfaStepUp {

  /** Throws {@link com.mediflow.prescription.support.RxExceptions.MfaStepUpRequiredException} if stale. */
  void requireFreshMfa();

  boolean isFresh();
}
