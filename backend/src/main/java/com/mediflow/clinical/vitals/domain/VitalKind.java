package com.mediflow.clinical.vitals.domain;

/**
 * How a vital's raw input is read. This decides which {@link VitalSign} subclass parses it. A new
 * vital (e.g. blood glucose) is a {@link #NUMERIC} row in {@code clinical_vital_definition} plus
 * a {@code vital_rule} row: no new code.
 */
public enum VitalKind {
  /** A single number, e.g. "72" (heart rate). Parsed by {@link NumericVital}. */
  NUMERIC,
  /** Text "systolic/diastolic", e.g. "120/80". Parsed by {@link BloodPressureVital}. */
  BLOOD_PRESSURE,
  /** Computed from other vitals (MAP, shock index); never entered by a nurse. */
  DERIVED
}
