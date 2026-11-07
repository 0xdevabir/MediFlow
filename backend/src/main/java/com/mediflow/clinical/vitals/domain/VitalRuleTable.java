package com.mediflow.clinical.vitals.domain;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every active {@code vital_rule} row, keyed by (population, vital type). Serializable for cache. */
public final class VitalRuleTable implements Serializable {

  private final Map<String, VitalRuleSpec> rules;

  public VitalRuleTable(Collection<VitalRuleSpec> specs) {
    Map<String, VitalRuleSpec> map = new HashMap<>();
    for (VitalRuleSpec s : specs) {
      map.put(key(s.population(), s.vitalType()), s);
    }
    this.rules = Map.copyOf(map);
  }

  private static String key(Population p, String type) {
    return p.name() + "|" + type;
  }

  public Optional<VitalRuleSpec> find(Population population, String vitalType) {
    return Optional.ofNullable(rules.get(key(population, vitalType)));
  }

  /** The rules for one population; missing types fall back to ADULT (flagged). */
  public VitalRuleSet forPopulation(Population population) {
    return new VitalRuleSet(this, population);
  }

  public List<VitalRuleSpec> all() {
    return List.copyOf(rules.values());
  }

  public int size() {
    return rules.size();
  }
}
