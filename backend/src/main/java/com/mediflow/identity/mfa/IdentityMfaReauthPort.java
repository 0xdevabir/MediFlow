package com.mediflow.identity.mfa;

import com.mediflow.shared.port.MfaReauthPort;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Shared {@link MfaReauthPort}: delegates to {@link MfaReauthService} with the caller's max-age
 * window (e-sign and other high-risk actions).
 */
@Component
public class IdentityMfaReauthPort implements MfaReauthPort {

  private final MfaReauthService reauth;

  public IdentityMfaReauthPort(MfaReauthService reauth) {
    this.reauth = reauth;
  }

  @Override
  public void requireFreshMfa(Duration maxAge) {
    reauth.requireFreshMfa(maxAge == null ? MfaSession.FRESH_WINDOW : maxAge);
  }
}
