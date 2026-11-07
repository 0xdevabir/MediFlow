package com.mediflow.prescription.ai;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration skeleton for RabbitMQ AI round-trip (10 s fallback with a slow worker).
 *
 * <p>Not run in this wave (coordinator: no Docker / no {@code mvnw verify}). When Docker is up,
 * start a {@code RabbitMQContainer}, override {@code spring.autoconfigure.exclude}, publish a slow
 * result after &gt;10 s, and assert {@link AiGatewayService#discardLateResult} dropped it.
 */
@SpringBootTest
@ActiveProfiles({"test", "dev"})
@Disabled("Needs RabbitMQ Testcontainers — see T06 report")
class AiGatewayIT {

  @Test
  void slowAi_afterTenSeconds_switchesToManual() {
    // placeholder for T14 / local verify
  }
}
