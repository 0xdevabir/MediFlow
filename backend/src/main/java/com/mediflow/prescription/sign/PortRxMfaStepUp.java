package com.mediflow.prescription.sign;

import com.mediflow.prescription.support.RxExceptions.MfaStepUpRequiredException;
import com.mediflow.shared.port.MfaReauthPort;
import java.time.Duration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * E-sign step-up via shared {@link MfaReauthPort} (V2-02 when present, else {@link
 * SessionMfaReauthPort}). Maps any step-up failure to the prescription web redirect exception.
 */
@Component
@Primary
public class PortRxMfaStepUp implements RxMfaStepUp {

  public static final Duration FRESH_WINDOW = Duration.ofMinutes(5);

  private final MfaReauthPort mfa;

  public PortRxMfaStepUp(MfaReauthPort mfa) {
    this.mfa = mfa;
  }

  @Override
  public void requireFreshMfa() {
    try {
      mfa.requireFreshMfa(FRESH_WINDOW);
    } catch (RuntimeException e) {
      if (e instanceof MfaStepUpRequiredException) {
        throw e;
      }
      if (e.getClass().getSimpleName().equals("MfaStepUpRequiredException")) {
        throw new MfaStepUpRequiredException();
      }
      throw e;
    }
  }

  @Override
  public boolean isFresh() {
    try {
      mfa.requireFreshMfa(FRESH_WINDOW);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }
}
