package com.mediflow.clinical.vitals.domain;

import com.mediflow.shared.domain.VitalClass;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One measured (or derived) vital sign. Every vital is this one type: each subclass reads its own
 * input ({@link #parse}) and classifies itself against the shared rule table ({@link #classify}).
 * The rest of the system only calls {@code v.classify(rules)} and never needs to know whether the
 * value was a number or text (R&D v1.3 §11, Figure 8).
 */
public abstract class VitalSign {

  private final VitalType type;

  protected VitalSign(VitalType type) {
    this.type = Objects.requireNonNull(type, "type");
  }

  public VitalType type() {
    return type;
  }

  /**
   * Reads raw input for a vital. The subclass is chosen by {@link VitalType#kind()}: blood
   * pressure text goes to {@link BloodPressureVital}, everything else to {@link NumericVital}.
   *
   * @throws VitalParseException if the input is empty, malformed or implausible
   */
  public static VitalSign parse(VitalType type, String raw) {
    Objects.requireNonNull(type, "type");
    return switch (type.kind()) {
      case BLOOD_PRESSURE -> BloodPressureVital.parse(type, raw);
      case NUMERIC, DERIVED -> NumericVital.parse(type, raw);
    };
  }

  /** Classification of this reading. A vital without a rule is never NORMAL (unknown ≠ safe). */
  public abstract VitalClass classify(VitalRuleSet rules);

  /** Whether every rule this vital needs was found for the population (else BORDERLINE). */
  public abstract boolean hasRules(VitalRuleSet rules);

  /** Whether any rule used came from the ADULT fallback instead of the patient's population. */
  public abstract boolean usesFallback(VitalRuleSet rules);

  /** Value for {@code vital_observation.value_numeric} ({@code null} if text only). */
  public abstract BigDecimal numericValue();

  /** Value for {@code vital_observation.value_text} ({@code null} if numeric only). */
  public abstract String textValue();

  /** Human-readable value without unit, e.g. "72" or "120/80". */
  public abstract String display();

  @Override
  public String toString() {
    return type.code() + "=" + display();
  }
}
