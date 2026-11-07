package com.mediflow.audit.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** T13 gauges for audit hash-chain health. */
@Component
public class AuditChainMetrics {

  private final AtomicInteger valid = new AtomicInteger(1);
  private final AtomicLong lastVerifiedEpochSeconds = new AtomicLong(0);

  public AuditChainMetrics(MeterRegistry registry) {
    registry.gauge("mediflow_audit_chain_valid", Tags.empty(), valid);
    registry.gauge(
        "mediflow_audit_chain_last_verified_timestamp_seconds",
        Tags.empty(),
        lastVerifiedEpochSeconds);
  }

  public void recordVerification(boolean ok, long epochSeconds) {
    valid.set(ok ? 1 : 0);
    lastVerifiedEpochSeconds.set(epochSeconds);
  }
}
