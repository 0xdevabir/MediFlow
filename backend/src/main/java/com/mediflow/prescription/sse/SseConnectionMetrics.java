package com.mediflow.prescription.sse;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** T13 gauge for live SSE connections. */
@Component
public class SseConnectionMetrics {

  private final AtomicInteger aiJobs = new AtomicInteger();

  public SseConnectionMetrics(MeterRegistry registry) {
    registry.gauge("mediflow_sse_connections", Tags.of("kind", "ai_job"), aiJobs);
  }

  public void connected() {
    aiJobs.incrementAndGet();
  }

  public void disconnected() {
    aiJobs.updateAndGet(v -> Math.max(0, v - 1));
  }
}
