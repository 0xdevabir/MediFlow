package com.mediflow.prescription.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiCircuitBreakerTest {

  @Test
  void opensAfterFailures_aiDownMeansManual() {
    AiCircuitBreaker cb = new AiCircuitBreaker(3, Duration.ofMinutes(1));
    assertTrue(cb.healthy());
    cb.onFailure();
    cb.onFailure();
    assertTrue(cb.allowRequest());
    cb.onFailure();
    assertEquals(AiCircuitBreaker.State.OPEN, cb.state());
    assertFalse(cb.healthy());
    assertFalse(cb.allowRequest());
  }

  @Test
  void successResets() {
    AiCircuitBreaker cb = new AiCircuitBreaker(2, Duration.ofMinutes(1));
    cb.onFailure();
    cb.onFailure();
    assertFalse(cb.healthy());
    // force half-open by constructing short open window
    AiCircuitBreaker fast = new AiCircuitBreaker(1, Duration.ofMillis(1));
    fast.onFailure();
    try {
      Thread.sleep(5);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    assertTrue(fast.allowRequest());
    fast.onSuccess();
    assertTrue(fast.healthy());
  }
}
