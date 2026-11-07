package com.mediflow.prescription.domain;

/**
 * Per-item outcome of the 85 % gate (PDF v1.3 §11 table).
 *
 * <ul>
 *   <li>{@link #PREFILLED}: item confidence &ge; threshold, medicine known, no critical conflict.
 *   <li>{@link #NEEDS_REVIEW}: confidence below threshold (amber) - must be confirmed/corrected.
 *   <li>{@link #UNKNOWN}: medicine not in the DGDA dictionary - left blank, treated as below threshold.
 *   <li>{@link #HARD_STOP}: critical conflict at any confidence - cannot be signed until corrected.
 *   <li>{@link #MANUAL}: entered by the doctor (still checked by the safety engine).
 * </ul>
 */
public enum GateStatus {
  PREFILLED,
  NEEDS_REVIEW,
  UNKNOWN,
  HARD_STOP,
  MANUAL
}
