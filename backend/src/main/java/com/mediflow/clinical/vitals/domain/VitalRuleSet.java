package com.mediflow.clinical.vitals.domain;

import com.mediflow.shared.domain.VitalClass;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Rules applicable to one patient population. A vital with no row for the population uses the
 * ADULT row ({@link #isFallback} reports it so the UI can warn); a vital with no row at all
 * classifies as BORDERLINE — never NORMAL, because unknown is never safe.
 */
public final class VitalRuleSet {

  private final VitalRuleTable table;
  private final Population population;

  VitalRuleSet(VitalRuleTable table, Population population) {
    this.table = Objects.requireNonNull(table);
    this.population = Objects.requireNonNull(population);
  }

  public Population population() {
    return population;
  }

  public Optional<VitalRuleSpec> rule(String vitalType) {
    Optional<VitalRuleSpec> own = table.find(population, vitalType);
    if (own.isPresent() || population == Population.ADULT) {
      return own;
    }
    return table.find(Population.ADULT, vitalType);
  }

  public boolean isFallback(String vitalType) {
    return population != Population.ADULT
        && table.find(population, vitalType).isEmpty()
        && table.find(Population.ADULT, vitalType).isPresent();
  }

  public VitalClass classify(String vitalType, BigDecimal value) {
    return rule(vitalType).map(r -> r.classify(value)).orElse(VitalClass.BORDERLINE);
  }
}
