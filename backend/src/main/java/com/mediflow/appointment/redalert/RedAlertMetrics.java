package com.mediflow.appointment.redalert;

import com.mediflow.shared.domain.RedAlert;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** T13 gauges/counters for open Red Alerts. */
@Component
public class RedAlertMetrics {

  private final AtomicLong open = new AtomicLong();
  private final Counter raised;

  public RedAlertMetrics(MeterRegistry registry) {
    registry.gauge("mediflow_red_alerts_open", Tags.empty(), open);
    this.raised = registry.counter("mediflow_red_alerts_raised_total");
  }

  public void onRaised(RedAlert alert) {
    open.incrementAndGet();
    raised.increment();
  }

  public void onClosed() {
    open.updateAndGet(v -> Math.max(0, v - 1));
  }

  public void setOpenCount(long count) {
    open.set(Math.max(0, count));
  }
}
