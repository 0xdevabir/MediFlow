package com.mediflow.shared.port.stub;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import com.mediflow.shared.port.ObjectStoragePort;
import com.mediflow.shared.port.PatientAccessPort;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LoggingPortStubsTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(LoggingPortStubs.class));

  @Test
  void providesEveryPortWhenModulesAreAbsent() {
    runner.run(
        ctx -> {
          assertThat(ctx)
              .hasSingleBean(AuditPort.class)
              .hasSingleBean(NotificationPort.class)
              .hasSingleBean(PatientAccessPort.class)
              .hasSingleBean(ObjectStoragePort.class)
              .hasSingleBean(ClockPort.class);
          // Deny by default until identity implements the assigned-patient / ER rules.
          PatientAccessPort access = ctx.getBean(PatientAccessPort.class);
          assertThat(access.isAssigned(UUID.randomUUID(), UUID.randomUUID())).isFalse();
          assertThat(access.isErAssigned(UUID.randomUUID(), UUID.randomUUID())).isFalse();
        });
  }

  @Test
  void backsOffWhenAModuleProvidesItsOwnImplementation() {
    Instant fixed = Instant.parse("2026-01-01T00:00:00Z");
    runner
        .withBean(ClockPort.class, () -> () -> fixed)
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(ClockPort.class);
              assertThat(ctx.getBean(ClockPort.class).now()).isEqualTo(fixed);
            });
  }
}

