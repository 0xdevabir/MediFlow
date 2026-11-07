package com.mediflow.identity.mfa;

import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Whether the login MFA gate is on. Property {@code mediflow.identity.mfa.enforce}; when unset it is
 * on everywhere except the {@code dev} profile (seeded dev users have no TOTP secret, and the T00
 * navigation tests sign in without MFA). Step-up ({@link MfaReauthService}) is always enforced.
 */
@Component
public class MfaEnforcement {

  public static final String PROPERTY = "mediflow.identity.mfa.enforce";

  private final boolean enforced;

  public MfaEnforcement(Environment env) {
    Boolean explicit = env.getProperty(PROPERTY, Boolean.class);
    this.enforced =
        explicit != null ? explicit : !Arrays.asList(env.getActiveProfiles()).contains("dev");
  }

  public boolean enforced() {
    return enforced;
  }
}
