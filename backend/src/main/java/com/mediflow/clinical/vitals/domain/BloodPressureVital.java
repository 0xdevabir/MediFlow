package com.mediflow.clinical.vitals.domain;

import com.mediflow.shared.domain.VitalClass;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Blood pressure read from text "systolic/diastolic", e.g. "120/80" (spaces allowed, whole mmHg).
 * It classifies as the higher of its systolic ({@code BP_SYSTOLIC}) and diastolic ({@code
 * BP_DIASTOLIC}) rule levels and provides the derived MAP and pulse pressure.
 */
public final class BloodPressureVital extends VitalSign {

  private static final Pattern FORMAT = Pattern.compile("^\\s*(\\d{1,3})\\s*/\\s*(\\d{1,3})\\s*$");

  /** Input guard for typos; real values outside this are not credible. */
  public static final int SYSTOLIC_MIN = 40;

  public static final int SYSTOLIC_MAX = 300;
  public static final int DIASTOLIC_MIN = 20;
  public static final int DIASTOLIC_MAX = 200;

  private final int systolic;
  private final int diastolic;

  public BloodPressureVital(VitalType type, int systolic, int diastolic) {
    super(type);
    this.systolic = systolic;
    this.diastolic = diastolic;
  }

  public static BloodPressureVital parse(VitalType type, String raw) {
    if (raw == null || raw.isBlank()) {
      throw new VitalParseException(type.code(), VitalParseException.EMPTY);
    }
    Matcher m = FORMAT.matcher(raw);
    if (!m.matches()) {
      throw new VitalParseException(type.code(), VitalParseException.BP_FORMAT, raw.trim());
    }
    int sys = Integer.parseInt(m.group(1));
    int dia = Integer.parseInt(m.group(2));
    if (sys < SYSTOLIC_MIN
        || sys > SYSTOLIC_MAX
        || dia < DIASTOLIC_MIN
        || dia > DIASTOLIC_MAX) {
      throw new VitalParseException(
          type.code(),
          VitalParseException.BP_RANGE,
          sys,
          dia,
          SYSTOLIC_MIN,
          SYSTOLIC_MAX,
          DIASTOLIC_MIN,
          DIASTOLIC_MAX);
    }
    if (sys <= dia) {
      throw new VitalParseException(type.code(), VitalParseException.BP_ORDER, sys, dia);
    }
    return new BloodPressureVital(type, sys, dia);
  }

  public int systolic() {
    return systolic;
  }

  public int diastolic() {
    return diastolic;
  }

  /** MAP = (SBP + 2 × DBP) ÷ 3. */
  public BigDecimal meanArterialPressure() {
    return BigDecimal.valueOf(systolic + 2L * diastolic)
        .divide(BigDecimal.valueOf(3), MathContext.DECIMAL64);
  }

  /** Pulse pressure = SBP − DBP (information only, not classified). */
  public int pulsePressure() {
    return systolic - diastolic;
  }

  @Override
  public VitalClass classify(VitalRuleSet rules) {
    VitalClass sys = rules.classify(VitalType.BP_SYSTOLIC, BigDecimal.valueOf(systolic));
    VitalClass dia = rules.classify(VitalType.BP_DIASTOLIC, BigDecimal.valueOf(diastolic));
    return VitalLevels.max(sys, dia);
  }

  @Override
  public boolean hasRules(VitalRuleSet rules) {
    return rules.rule(VitalType.BP_SYSTOLIC).isPresent()
        && rules.rule(VitalType.BP_DIASTOLIC).isPresent();
  }

  @Override
  public boolean usesFallback(VitalRuleSet rules) {
    return rules.isFallback(VitalType.BP_SYSTOLIC) || rules.isFallback(VitalType.BP_DIASTOLIC);
  }

  /** Systolic, so trend queries and FHIR mapping have a number; the full value is in text. */
  @Override
  public BigDecimal numericValue() {
    return BigDecimal.valueOf(systolic);
  }

  @Override
  public String textValue() {
    return systolic + "/" + diastolic;
  }

  @Override
  public String display() {
    return textValue();
  }
}
