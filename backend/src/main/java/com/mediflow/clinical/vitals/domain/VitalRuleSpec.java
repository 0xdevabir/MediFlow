package com.mediflow.clinical.vitals.domain;

import com.mediflow.shared.domain.VitalClass;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One {@code vital_rule} row. All ranges are <b>half-open</b> {@code [min, max)}; a {@code null}
 * bound is open:
 *
 * <ul>
 *   <li>CRITICAL if {@code value < criticalMin} or {@code value >= criticalMax};
 *   <li>otherwise NORMAL if {@code normalMin <= value < normalMax};
 *   <li>otherwise BORDERLINE. Values in no listed band (e.g. adult HR 40–49, which the published
 *       table leaves uncovered) are BORDERLINE, never NORMAL: unknown is never safe.
 * </ul>
 *
 * <p>The borderline band is stored for display/governance; classification does not need it. The
 * adult rows in {@code V301} encode the published table at its 0.1 / 1 unit resolution, e.g. HR
 * "60–100" → {@code [60, 101)}, temperature "≤ 35.0" → {@code criticalMin = 35.1}.
 */
public record VitalRuleSpec(
    String vitalType,
    Population population,
    String unit,
    BigDecimal normalMin,
    BigDecimal normalMax,
    BigDecimal borderlineMin,
    BigDecimal borderlineMax,
    BigDecimal criticalMin,
    BigDecimal criticalMax)
    implements Serializable {

  public VitalRuleSpec {
    Objects.requireNonNull(vitalType, "vitalType");
    Objects.requireNonNull(population, "population");
  }

  public VitalClass classify(BigDecimal value) {
    Objects.requireNonNull(value, "value");
    if (criticalMin != null && value.compareTo(criticalMin) < 0) {
      return VitalClass.CRITICAL;
    }
    if (criticalMax != null && value.compareTo(criticalMax) >= 0) {
      return VitalClass.CRITICAL;
    }
    boolean aboveNormalMin = normalMin == null || value.compareTo(normalMin) >= 0;
    boolean belowNormalMax = normalMax == null || value.compareTo(normalMax) < 0;
    if (aboveNormalMin && belowNormalMax && (normalMin != null || normalMax != null)) {
      return VitalClass.NORMAL;
    }
    return VitalClass.BORDERLINE;
  }

  /**
   * Consistency check for rule edits (T11): the normal band must lie inside the critical bounds and
   * be non-empty, and at least one bound must exist.
   *
   * @return {@code null} if valid, else a short reason
   */
  public String validate() {
    if (normalMin == null && normalMax == null && criticalMin == null && criticalMax == null) {
      return "no bounds";
    }
    if (normalMin != null && normalMax != null && normalMin.compareTo(normalMax) >= 0) {
      return "normal range empty";
    }
    if (criticalMin != null && normalMin != null && normalMin.compareTo(criticalMin) < 0) {
      return "normal below critical-low bound";
    }
    if (criticalMax != null && normalMax != null && normalMax.compareTo(criticalMax) > 0) {
      return "normal above critical-high bound";
    }
    if (criticalMin != null && criticalMax != null && criticalMin.compareTo(criticalMax) >= 0) {
      return "critical bounds overlap";
    }
    return null;
  }
}
