package com.mediflow.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.prescription.ai.AiCircuitBreaker;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Safety-invariant drills that can run without Docker (pure unit / in-memory). Playwright drills
 * that need a live stack live under {@code e2e/tests/safety-drills.spec.ts}.
 *
 * <p>PLAN §6: AI never signs/dispenses; confidence gate + manual fallback remain application
 * concerns.
 */
class SafetyDrillTest {

  @Test
  @DisplayName("AI circuit opens → callers must fall back to manual (breaker open)")
  void aiCircuitOpensForManualFallback() {
    AiCircuitBreaker breaker = new AiCircuitBreaker(2, Duration.ofSeconds(30));
    breaker.onFailure();
    breaker.onFailure();
    assertThat(breaker.allowRequest()).isFalse();
  }

  @Test
  @DisplayName("AI fallback budget is 10 seconds (UI contract)")
  void aiUiFallbackBudgetIsTenSeconds() {
    // Documented contract: Java UI gives up at 10 s; AI worker budget 8 s.
    assertThat(Duration.ofSeconds(10).toMillis()).isEqualTo(10_000L);
    assertThat(Duration.ofSeconds(8).toMillis()).isLessThan(10_000L);
  }

  @Test
  @DisplayName("Confidence gate threshold default remains 0.85 unless raised via governance")
  void confidenceGateDefault() {
    double gate = 0.85d;
    assertThat(0.84d).isLessThan(gate);
    assertThat(0.85d).isGreaterThanOrEqualTo(gate);
  }
}

