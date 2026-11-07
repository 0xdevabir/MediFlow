package com.mediflow.clinical.vitals;

import com.mediflow.clinical.vitals.domain.Population;
import com.mediflow.clinical.vitals.domain.VitalKind;
import com.mediflow.clinical.vitals.domain.VitalRuleSpec;
import com.mediflow.clinical.vitals.domain.VitalRuleTable;
import com.mediflow.clinical.vitals.domain.VitalType;
import java.math.BigDecimal;
import java.util.List;

/**
 * The adult defaults exactly as seeded by {@code V301__clinical_vital_rules_seed.sql} (R&D v1.3 §11
 * rule table, half-open encoding). {@code VitalRuleSeedIT} checks the DB rows equal these.
 */
public final class AdultDefaultRules {

  private AdultDefaultRules() {}

  public static final VitalType HR =
      new VitalType(
          "HR", VitalKind.NUMERIC, "/min", "8867-4", "Heart rate", "হৃদস্পন্দন", bd("20"),
          bd("300"), 20, true);
  public static final VitalType RR =
      new VitalType(
          "RR", VitalKind.NUMERIC, "/min", "9279-1", "Respiratory rate", "শ্বাসের হার", bd("1"),
          bd("80"), 30, true);
  public static final VitalType TEMP =
      new VitalType(
          "TEMP", VitalKind.NUMERIC, "Cel", "8310-5", "Body temperature", "শরীরের তাপমাত্রা",
          bd("25"), bd("45"), 40, true);
  public static final VitalType SPO2 =
      new VitalType(
          "SPO2", VitalKind.NUMERIC, "%", "59408-5", "Oxygen saturation (SpO2)", "অক্সিজেন স্যাচুরেশন",
          bd("50"), bd("100"), 50, true);
  public static final VitalType BP =
      new VitalType(
          "BP", VitalKind.BLOOD_PRESSURE, "mm[Hg]", "85354-9", "Blood pressure", "রক্তচাপ", null,
          null, 10, true);

  public static List<VitalRuleSpec> specs() {
    return List.of(
        spec("HR", "/min", "60", "101", "50", "130", "40", "130"),
        spec("RR", "/min", "12", "21", "9", "25", "9", "25"),
        spec("TEMP", "Cel", "36.1", "37.3", "35.1", "39.5", "35.1", "39.5"),
        spec("SPO2", "%", "95", null, "91", "95", "91", null),
        spec("BP_SYSTOLIC", "mm[Hg]", null, "120", "120", "180", "90", "180"),
        spec("BP_DIASTOLIC", "mm[Hg]", null, "80", "80", "120", null, "120"),
        spec("MAP", "mm[Hg]", "65", null, null, null, "65", null),
        spec("SHOCK_INDEX", "{ratio}", null, "0.9", "0.9", "1.0", null, "1.0"));
  }

  public static VitalRuleTable table() {
    return new VitalRuleTable(specs());
  }

  static VitalRuleSpec spec(
      String type,
      String unit,
      String nMin,
      String nMax,
      String bMin,
      String bMax,
      String cMin,
      String cMax) {
    return new VitalRuleSpec(
        type,
        Population.ADULT,
        unit,
        bd(nMin),
        bd(nMax),
        bd(bMin),
        bd(bMax),
        bd(cMin),
        bd(cMax));
  }

  static BigDecimal bd(String s) {
    return s == null ? null : new BigDecimal(s);
  }
}
