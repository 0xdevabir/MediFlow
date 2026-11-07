package com.mediflow.lab.service;

import com.mediflow.lab.domain.LabReferenceRange;
import com.mediflow.lab.domain.ResultFlag;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Pure logic: pick the right reference range and flag a numeric value against it. */
public final class ResultFlagCalculator {

  private ResultFlagCalculator() {}

  /**
   * Critical limits win over the reference range: {@code <= criticalLow} → CRITICAL_LOW, {@code >=
   * criticalHigh} → CRITICAL_HIGH, then {@code < refLow} → LOW, {@code > refHigh} → HIGH. A null
   * value is never "normal" (unknown is never safe), so callers must reject it before flagging.
   */
  public static ResultFlag flag(
      BigDecimal value,
      BigDecimal refLow,
      BigDecimal refHigh,
      BigDecimal criticalLow,
      BigDecimal criticalHigh) {
    if (value == null) {
      throw new IllegalArgumentException("value is required");
    }
    if (criticalLow != null && value.compareTo(criticalLow) <= 0) {
      return ResultFlag.CRITICAL_LOW;
    }
    if (criticalHigh != null && value.compareTo(criticalHigh) >= 0) {
      return ResultFlag.CRITICAL_HIGH;
    }
    if (refLow != null && value.compareTo(refLow) < 0) {
      return ResultFlag.LOW;
    }
    if (refHigh != null && value.compareTo(refHigh) > 0) {
      return ResultFlag.HIGH;
    }
    return ResultFlag.NORMAL;
  }

  public static ResultFlag flag(BigDecimal value, LabReferenceRange range) {
    return flag(
        value, range.getRefLow(), range.getRefHigh(), range.getCriticalLow(), range.getCriticalHigh());
  }

  /**
   * Ranges of one analyte: prefer the patient's sex-specific row, else the sex-neutral one, else any.
   */
  public static Optional<LabReferenceRange> pick(
      Collection<LabReferenceRange> rangesOfAnalyte, String patientSex) {
    String sex = normaliseSex(patientSex);
    return rangesOfAnalyte.stream()
        .min(
            Comparator.comparingInt(
                r -> {
                  if (sex != null && sex.equals(r.getSex())) {
                    return 0;
                  }
                  return r.getSex() == null ? 1 : 2;
                }));
  }

  /** "male"/"M"/"MALE" → "M"; "female"/"F" → "F"; anything else → null. */
  public static String normaliseSex(String sex) {
    if (sex == null || sex.isBlank()) {
      return null;
    }
    String s = sex.trim().toUpperCase(Locale.ROOT);
    if (s.startsWith("M")) {
      return "M";
    }
    if (s.startsWith("F")) {
      return "F";
    }
    return null;
  }

  public static boolean anyAbnormal(List<ResultFlag> flags) {
    return flags.stream().anyMatch(ResultFlag::isAbnormal);
  }

  public static boolean anyCritical(List<ResultFlag> flags) {
    return flags.stream().anyMatch(ResultFlag::isCritical);
  }
}
