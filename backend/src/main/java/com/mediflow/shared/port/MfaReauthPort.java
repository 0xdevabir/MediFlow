package com.mediflow.shared.port;

import java.time.Duration;

/**
 * Step-up MFA gate for high-risk actions (e-sign, break-glass confirmations).
 *
 * <p><b>Implementor:</b> V2-02 ({@code com.mediflow.identity}). <b>Consumers:</b> V2-07 prescription
 * e-sign. Implementations should throw a module-defined step-up exception when the signed-in
 * session has no fresh TOTP within {@code maxAge}.
 */
public interface MfaReauthPort {

  /** Passes when MFA was verified within {@code maxAge}; otherwise throws for the web layer. */
  void requireFreshMfa(Duration maxAge);
}
