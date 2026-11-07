package com.mediflow.lab.domain;

/** Flag of one result value, computed from its reference / critical range. */
public enum ResultFlag {
  NORMAL,
  LOW,
  HIGH,
  /** Non-numeric value outside a qualitative expectation (set manually by the lab). */
  ABNORMAL,
  CRITICAL_LOW,
  CRITICAL_HIGH;

  public boolean isAbnormal() {
    return this != NORMAL;
  }

  public boolean isCritical() {
    return this == CRITICAL_LOW || this == CRITICAL_HIGH;
  }
}
