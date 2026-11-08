package com.mediflow.prescription.sign;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediflow.prescription.support.RxExceptions.MfaStepUpRequiredException;
import com.mediflow.shared.port.MfaReauthPort;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PortRxMfaStepUpTest {

  @Test
  void requireFreshDelegatesAndMapsPrescriptionException() {
    MfaReauthPort port =
        maxAge -> {
          throw new MfaStepUpRequiredException();
        };
    PortRxMfaStepUp stepUp = new PortRxMfaStepUp(port);
    assertThrows(MfaStepUpRequiredException.class, stepUp::requireFreshMfa);
    assertFalse(stepUp.isFresh());
  }

  @Test
  void freshWhenPortPasses() {
    MfaReauthPort port = maxAge -> {};
    PortRxMfaStepUp stepUp = new PortRxMfaStepUp(port);
    stepUp.requireFreshMfa();
    assertTrue(stepUp.isFresh());
    assertEqualsFiveMinutes(PortRxMfaStepUp.FRESH_WINDOW);
  }

  private static void assertEqualsFiveMinutes(Duration d) {
    assertTrue(Duration.ofMinutes(5).equals(d));
  }
}
