package com.mediflow.prescription.sign;

/**
 * MFA step-up for e-sign. Implemented by {@link PortRxMfaStepUp} over shared {@link
 * com.mediflow.shared.port.MfaReauthPort}.
 */
public interface RxMfaStepUp {

  /** Throws {@link com.mediflow.prescription.support.RxExceptions.MfaStepUpRequiredException} if stale. */
  void requireFreshMfa();

  boolean isFresh();
}
