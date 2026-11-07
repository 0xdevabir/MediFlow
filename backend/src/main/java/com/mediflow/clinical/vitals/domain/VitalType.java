package com.mediflow.clinical.vitals.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One kind of vital sign, as defined by a row in {@code clinical_vital_definition}. It is a value
 * object rather than an enum, so adding a vital is data-only.
 *
 * @param code rule key in {@code vital_rule.vital_type} (for BP the rules are {@code
 *     BP_SYSTOLIC}/{@code BP_DIASTOLIC})
 * @param ucumUnit UCUM unit code stored in {@code vital_observation.unit}
 * @param loincCode LOINC code of the observation (BP: panel code)
 * @param plausibleMin lowest value accepted as input (typo guard); {@code null} = no lower guard
 * @param plausibleMax highest value accepted as input; {@code null} = no upper guard
 */
public record VitalType(
    String code,
    VitalKind kind,
    String ucumUnit,
    String loincCode,
    String displayEn,
    String displayBn,
    BigDecimal plausibleMin,
    BigDecimal plausibleMax,
    int sortOrder,
    boolean nurseEntry)
    implements Serializable {

  public static final String HR = "HR";
  public static final String RR = "RR";
  public static final String TEMP = "TEMP";
  public static final String SPO2 = "SPO2";
  public static final String BP = "BP";
  public static final String BP_SYSTOLIC = "BP_SYSTOLIC";
  public static final String BP_DIASTOLIC = "BP_DIASTOLIC";
  public static final String MAP = "MAP";
  public static final String SHOCK_INDEX = "SHOCK_INDEX";
  public static final String PULSE_PRESSURE = "PULSE_PRESSURE";

  /** LOINC codes for the BP components (used when BP is stored or mapped to FHIR). */
  public static final String LOINC_SYSTOLIC = "8480-6";

  public static final String LOINC_DIASTOLIC = "8462-4";

  /** Built-in definition of mean arterial pressure (used if the catalog lacks the row). */
  public static final VitalType MAP_DEFAULT =
      new VitalType(
          MAP,
          VitalKind.DERIVED,
          "mm[Hg]",
          "8478-0",
          "Mean arterial pressure",
          "গড় ধমনী চাপ",
          null,
          null,
          100,
          false);

  /** Built-in definition of shock index HR/SBP (no LOINC code is assigned for it). */
  public static final VitalType SHOCK_INDEX_DEFAULT =
      new VitalType(
          SHOCK_INDEX,
          VitalKind.DERIVED,
          "{ratio}",
          null,
          "Shock index",
          "শক ইনডেক্স",
          null,
          null,
          110,
          false);

  public VitalType {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(ucumUnit, "ucumUnit");
    if (code.isBlank()) {
      throw new IllegalArgumentException("Vital code must not be blank");
    }
  }

  /** Minimal numeric type, e.g. for tests or rule-only vitals. */
  public static VitalType numeric(String code, String ucumUnit) {
    return new VitalType(
        code, VitalKind.NUMERIC, ucumUnit, null, code, code, null, null, 0, true);
  }

  public String display(String lang) {
    String value = "bn".equals(lang) ? displayBn : displayEn;
    return value == null || value.isBlank() ? code : value;
  }
}
