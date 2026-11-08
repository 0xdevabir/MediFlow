package com.mediflow.prescription.sign;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;

/**
 * Compatibility helpers for tests that previously used the session MFA bean. Production e-sign
 * uses {@link PortRxMfaStepUp} → {@link com.mediflow.shared.port.MfaReauthPort}.
 */
@Deprecated
public final class SessionRxMfaStepUp {

  public static final String VERIFIED_AT = SessionMfaReauthPort.VERIFIED_AT;
  public static final String VERIFIED_FOR = SessionMfaReauthPort.VERIFIED_FOR;
  public static final Duration FRESH_WINDOW = PortRxMfaStepUp.FRESH_WINDOW;

  private SessionRxMfaStepUp() {}

  /** Test helper: mark MFA fresh in the current session. */
  public static void markFresh(HttpServletRequest request, String username, Instant now) {
    SessionMfaReauthPort.markFresh(request, username, now);
  }
}
