package com.mediflow.clinical.vitals.domain;

import com.mediflow.shared.domain.VitalClass;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Classifies a vital set: each vital via {@code classify()}, then the derived values from the R&D
 * formula table, then the patient level as the maximum of all of them.
 *
 * <ul>
 *   <li>MAP = (SBP + 2·DBP) / 3, classified by the {@code MAP} rule (adult: &lt; 65 → CRITICAL);
 *   <li>shock index = HR / SBP, classified by the {@code SHOCK_INDEX} rule (adult: ≥ 0.9
 *       BORDERLINE, ≥ 1.0 CRITICAL);
 *   <li>pulse pressure = SBP − DBP, information only.
 * </ul>
 *
 * Derived values are themselves {@link NumericVital}s, so their limits live in the same rule table.
 */
public final class VitalSetClassifier {

  private final VitalType mapType;
  private final VitalType shockIndexType;

  public VitalSetClassifier() {
    this(VitalType.MAP_DEFAULT, VitalType.SHOCK_INDEX_DEFAULT);
  }

  public VitalSetClassifier(VitalType mapType, VitalType shockIndexType) {
    this.mapType = mapType;
    this.shockIndexType = shockIndexType;
  }

  public VitalSetResult classify(List<VitalSign> signs, VitalRuleSet rules) {
    List<VitalReading> readings = new ArrayList<>();
    for (VitalSign s : signs) {
      readings.add(read(s, rules, false));
    }
    Optional<BloodPressureVital> bp =
        signs.stream()
            .filter(BloodPressureVital.class::isInstance)
            .map(BloodPressureVital.class::cast)
            .findFirst();
    Optional<NumericVital> hr =
        signs.stream()
            .filter(NumericVital.class::isInstance)
            .map(NumericVital.class::cast)
            .filter(v -> VitalType.HR.equals(v.type().code()))
            .findFirst();
    Integer pulsePressure = null;
    if (bp.isPresent()) {
      BloodPressureVital b = bp.get();
      readings.add(read(new NumericVital(mapType, b.meanArterialPressure()), rules, true));
      pulsePressure = b.pulsePressure();
      if (hr.isPresent()) {
        BigDecimal si =
            hr.get().value().divide(BigDecimal.valueOf(b.systolic()), MathContext.DECIMAL64);
        readings.add(read(new NumericVital(shockIndexType, si), rules, true));
      }
    }
    VitalClass level = VitalLevels.max(readings.stream().map(VitalReading::level).toList());
    return new VitalSetResult(rules.population(), readings, pulsePressure, level);
  }

  private static VitalReading read(VitalSign s, VitalRuleSet rules, boolean derived) {
    return new VitalReading(
        s, s.classify(rules), derived, !s.hasRules(rules), s.usesFallback(rules));
  }
}
