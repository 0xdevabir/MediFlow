package com.mediflow.prescription.safety;

import com.mediflow.prescription.domain.GateStatus;
import com.mediflow.shared.domain.ConflictSeverity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Per-item 85 % gate (PLAN §6 / DOCX Table 20 / PDF §11).
 *
 * <p>Item confidence = {@code min(drug, dose, frequency)}. Threshold comes from {@code
 * system_setting ai.confidence.threshold} (default 0.85); this module only reads it.
 */
public final class ConfidenceGate {

  public static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.85");

  private ConfidenceGate() {}

  /** Overall item confidence = min of the three required fields; nulls count as 0. */
  public static BigDecimal itemConfidence(BigDecimal drug, BigDecimal dose, BigDecimal frequency) {
    return min(nz(drug), nz(dose), nz(frequency));
  }

  /**
   * Apply the gate for an AI-proposed item.
   *
   * @param brandKnown whether the brand/generic resolved against the DGDA dictionary
   * @param maxConflictSeverity highest conflict severity already found for this item (may be null)
   */
  public static GateStatus decide(
      BigDecimal drug,
      BigDecimal dose,
      BigDecimal frequency,
      boolean brandKnown,
      ConflictSeverity maxConflictSeverity,
      BigDecimal threshold) {
    BigDecimal t = threshold == null ? DEFAULT_THRESHOLD : threshold;
    if (maxConflictSeverity == ConflictSeverity.CRITICAL) {
      return GateStatus.HARD_STOP;
    }
    if (!brandKnown) {
      return GateStatus.UNKNOWN;
    }
    BigDecimal conf = itemConfidence(drug, dose, frequency);
    if (conf.compareTo(t) >= 0) {
      return GateStatus.PREFILLED;
    }
    return GateStatus.NEEDS_REVIEW;
  }

  /** Manual items are still safety-checked but skip the AI confidence gate. */
  public static GateStatus forManual(ConflictSeverity maxConflictSeverity) {
    if (maxConflictSeverity == ConflictSeverity.CRITICAL) {
      return GateStatus.HARD_STOP;
    }
    return GateStatus.MANUAL;
  }

  public static BigDecimal parseThreshold(String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT_THRESHOLD;
    }
    try {
      BigDecimal v = new BigDecimal(raw.trim());
      if (v.compareTo(BigDecimal.ZERO) < 0 || v.compareTo(BigDecimal.ONE) > 0) {
        return DEFAULT_THRESHOLD;
      }
      return v.setScale(4, RoundingMode.HALF_UP);
    } catch (NumberFormatException e) {
      return DEFAULT_THRESHOLD;
    }
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static BigDecimal min(BigDecimal a, BigDecimal b, BigDecimal c) {
    BigDecimal m = a.compareTo(b) <= 0 ? a : b;
    return m.compareTo(c) <= 0 ? m : c;
  }

  public static boolean meetsThreshold(BigDecimal confidence, BigDecimal threshold) {
    Objects.requireNonNull(confidence);
    BigDecimal t = threshold == null ? DEFAULT_THRESHOLD : threshold;
    return confidence.compareTo(t) >= 0;
  }
}
