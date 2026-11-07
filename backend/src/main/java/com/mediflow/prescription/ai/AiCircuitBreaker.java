package com.mediflow.prescription.ai;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Tiny circuit breaker (Resilience4j not on the classpath — see {@code docs/plan/requests/T06.md}).
 * Opens after {@code failureThreshold} failures; half-open after {@code openDuration}.
 */
@Component
public class AiCircuitBreaker {

  private final int failureThreshold;
  private final Duration openDuration;
  private final AtomicInteger failures = new AtomicInteger();
  private final AtomicLong openedAtMillis = new AtomicLong(0);
  private volatile State state = State.CLOSED;

  public AiCircuitBreaker() {
    this(5, Duration.ofSeconds(30));
  }

  public AiCircuitBreaker(int failureThreshold, Duration openDuration) {
    this.failureThreshold = failureThreshold;
    this.openDuration = openDuration;
  }

  public enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  public synchronized boolean allowRequest() {
    if (state == State.CLOSED) {
      return true;
    }
    if (state == State.OPEN) {
      if (System.currentTimeMillis() - openedAtMillis.get() >= openDuration.toMillis()) {
        state = State.HALF_OPEN;
        return true;
      }
      return false;
    }
    return true; // HALF_OPEN: allow one probe
  }

  public synchronized void onSuccess() {
    failures.set(0);
    state = State.CLOSED;
    openedAtMillis.set(0);
  }

  public synchronized void onFailure() {
    int f = failures.incrementAndGet();
    if (state == State.HALF_OPEN || f >= failureThreshold) {
      state = State.OPEN;
      openedAtMillis.set(System.currentTimeMillis());
    }
  }

  public State state() {
    allowRequest(); // refresh OPEN→HALF_OPEN
    return state;
  }

  public boolean healthy() {
    return state() != State.OPEN;
  }
}
