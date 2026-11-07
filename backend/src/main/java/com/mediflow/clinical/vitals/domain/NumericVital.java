package com.mediflow.clinical.vitals.domain;

import com.mediflow.shared.domain.VitalClass;
import java.math.BigDecimal;
import java.util.Objects;

/** A vital read from one number: heart rate, respiratory rate, temperature, SpO2, glucose, MAP … */
public final class NumericVital extends VitalSign {

  private final BigDecimal value;

  public NumericVital(VitalType type, BigDecimal value) {
    super(type);
    this.value = Objects.requireNonNull(value, "value");
  }

  public static NumericVital parse(VitalType type, String raw) {
    if (raw == null || raw.isBlank()) {
      throw new VitalParseException(type.code(), VitalParseException.EMPTY);
    }
    String text = raw.trim().replace(',', '.');
    BigDecimal value;
    try {
      value = new BigDecimal(text);
    } catch (NumberFormatException e) {
      throw new VitalParseException(type.code(), VitalParseException.NOT_A_NUMBER, raw.trim());
    }
    if ((type.plausibleMin() != null && value.compareTo(type.plausibleMin()) < 0)
        || (type.plausibleMax() != null && value.compareTo(type.plausibleMax()) > 0)) {
      throw new VitalParseException(
          type.code(),
          VitalParseException.OUT_OF_RANGE,
          value.toPlainString(),
          plain(type.plausibleMin()),
          plain(type.plausibleMax()));
    }
    return new NumericVital(type, value);
  }

  private static String plain(BigDecimal v) {
    return v == null ? "—" : v.stripTrailingZeros().toPlainString();
  }

  public BigDecimal value() {
    return value;
  }

  @Override
  public VitalClass classify(VitalRuleSet rules) {
    return rules.classify(type().code(), value);
  }

  @Override
  public boolean hasRules(VitalRuleSet rules) {
    return rules.rule(type().code()).isPresent();
  }

  @Override
  public boolean usesFallback(VitalRuleSet rules) {
    return rules.isFallback(type().code());
  }

  @Override
  public BigDecimal numericValue() {
    return value;
  }

  @Override
  public String textValue() {
    return null;
  }

  @Override
  public String display() {
    return value.stripTrailingZeros().toPlainString();
  }
}
