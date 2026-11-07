package com.mediflow.identity.login;

import java.time.Duration;
import java.time.Instant;

/** 5 consecutive failures → locked for 15 minutes. Pure logic, unit-tested. */
public final class LockoutPolicy {

  public static final int MAX_FAILED_ATTEMPTS = 5;
  public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

  private LockoutPolicy() {}

  public static boolean isLocked(LoginState state, Instant now) {
    return state != null && state.getLockedUntil() != null && now.isBefore(state.getLockedUntil());
  }

  /** Applies one failed attempt; returns true if this failure locked the account. */
  public static boolean recordFailure(LoginState state, Instant now) {
    if (state.getLockedUntil() != null && !now.isBefore(state.getLockedUntil())) {
      // previous lock expired: start counting afresh
      state.setLockedUntil(null);
      state.setFailedAttempts(0);
    }
    state.setFailedAttempts(state.getFailedAttempts() + 1);
    state.setLastFailedAt(now);
    if (state.getFailedAttempts() >= MAX_FAILED_ATTEMPTS && state.getLockedUntil() == null) {
      state.setLockedUntil(now.plus(LOCK_DURATION));
      return true;
    }
    return false;
  }

  public static void recordSuccess(LoginState state) {
    state.setFailedAttempts(0);
    state.setLockedUntil(null);
    state.setLastFailedAt(null);
  }
}
