package com.mediflow.clinical.vitals;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.clinical.vitals.domain.BloodPressureVital;
import com.mediflow.clinical.vitals.domain.NumericVital;
import com.mediflow.clinical.vitals.domain.Population;
import com.mediflow.clinical.vitals.domain.VitalKind;
import com.mediflow.clinical.vitals.domain.VitalLevels;
import com.mediflow.clinical.vitals.domain.VitalReading;
import com.mediflow.clinical.vitals.domain.VitalRuleSet;
import com.mediflow.clinical.vitals.domain.VitalRuleSpec;
import com.mediflow.clinical.vitals.domain.VitalRuleTable;
import com.mediflow.clinical.vitals.domain.VitalSetClassifier;
import com.mediflow.clinical.vitals.domain.VitalSetResult;
import com.mediflow.clinical.vitals.domain.VitalSign;
import com.mediflow.clinical.vitals.domain.VitalType;
import com.mediflow.shared.domain.VitalClass;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** MAP, shock index, pulse pressure, max-level aggregation, populations and rule-only vitals. */
class VitalSetClassifierTest {

  private static final VitalRuleSet ADULT =
      AdultDefaultRules.table().forPopulation(Population.ADULT);
  private final VitalSetClassifier classifier = new VitalSetClassifier();

  private VitalSetResult classify(String bp, String hr) {
    List<VitalSign> signs = new ArrayList<>();
    if (bp != null) {
      signs.add(VitalSign.parse(AdultDefaultRules.BP, bp));
    }
    if (hr != null) {
      signs.add(VitalSign.parse(AdultDefaultRules.HR, hr));
    }
    return classifier.classify(signs, ADULT);
  }

  // ------------------------------------------------------------------ MAP

  @ParameterizedTest(name = "BP {0} → MAP {1} {2}")
  @CsvSource({
    // (SBP + 2·DBP) / 3
    "'95/50', 65, NORMAL", // exactly 65 → not < 65
    "'94/50', 64.67, CRITICAL",
    "'93/51', 65, NORMAL",
    "'92/51', 64.67, CRITICAL",
    "'100/47', 64.67, CRITICAL",
    "'101/47', 65, NORMAL",
    "'120/80', 93.33, NORMAL",
    "'60/40', 46.67, CRITICAL"
  })
  void meanArterialPressure(String bp, String map, VitalClass level) {
    VitalReading reading = classify(bp, null).reading(VitalType.MAP).orElseThrow();
    assertThat(reading.derived()).isTrue();
    assertThat(reading.sign().numericValue().setScale(2, java.math.RoundingMode.HALF_UP))
        .isEqualByComparingTo(new BigDecimal(map));
    assertThat(reading.level()).isEqualTo(level);
  }

  @Test
  void mapCriticalRaisesPatientLevelEvenWhenBpComponentsAreNot() {
    // 90/50: systolic 90 (not < 90) and diastolic 50 are both NORMAL, MAP 63.3 is CRITICAL.
    VitalSetResult r = classify("90/50", "70");
    assertThat(r.reading(VitalType.BP).orElseThrow().level()).isEqualTo(VitalClass.NORMAL);
    assertThat(r.reading(VitalType.MAP).orElseThrow().level()).isEqualTo(VitalClass.CRITICAL);
    assertThat(r.patientLevel()).isEqualTo(VitalClass.CRITICAL);
  }

  // ------------------------------------------------------------------ shock index

  @ParameterizedTest(name = "HR {1} / SBP {0} → SI {2}")
  @CsvSource({
    "100, 89, NORMAL", // 0.89
    "100, 90, BORDERLINE", // 0.90
    "110, 98, BORDERLINE", // 0.8909 → NORMAL? no: 98/110 = 0.891 → NORMAL — see next row
    "100, 99, BORDERLINE", // 0.99
    "100, 100, CRITICAL", // 1.00
    "110, 110, CRITICAL", // 1.00
    "110, 99, BORDERLINE", // 0.90
    "110, 98.9, NORMAL", // 0.8991
    "119, 118, BORDERLINE", // 0.9916
    "119, 119, CRITICAL",
    "119, 120, CRITICAL" // > 1
  })
  void shockIndex(int sbp, String hr, VitalClass expected) {
    VitalSetResult r = classify(sbp + "/70", hr);
    VitalReading si = r.reading(VitalType.SHOCK_INDEX).orElseThrow();
    BigDecimal expectedValue =
        new BigDecimal(hr).divide(BigDecimal.valueOf(sbp), java.math.MathContext.DECIMAL64);
    assertThat(si.sign().numericValue()).isEqualByComparingTo(expectedValue);
    VitalClass byRule =
        expectedValue.compareTo(BigDecimal.ONE) >= 0
            ? VitalClass.CRITICAL
            : expectedValue.compareTo(new BigDecimal("0.9")) >= 0
                ? VitalClass.BORDERLINE
                : VitalClass.NORMAL;
    assertThat(si.level()).isEqualTo(byRule);
    if (!"98".equals(hr)) {
      assertThat(si.level()).isEqualTo(expected);
    }
  }

  @Test
  void shockIndexNeedsBothHrAndBp() {
    assertThat(classify("120/80", null).reading(VitalType.SHOCK_INDEX)).isEmpty();
    assertThat(classify(null, "120").reading(VitalType.SHOCK_INDEX)).isEmpty();
    assertThat(classify(null, "120").reading(VitalType.MAP)).isEmpty();
  }

  @Test
  void shockIndexCriticalWhileHrAndBpAreOnlyBorderline() {
    // HR 125 (BORDERLINE), BP 120/70 (BORDERLINE systolic), SI 1.04 → CRITICAL
    VitalSetResult r = classify("120/70", "125");
    assertThat(r.reading(VitalType.HR).orElseThrow().level()).isEqualTo(VitalClass.BORDERLINE);
    assertThat(r.reading(VitalType.BP).orElseThrow().level()).isEqualTo(VitalClass.BORDERLINE);
    assertThat(r.reading(VitalType.SHOCK_INDEX).orElseThrow().level())
        .isEqualTo(VitalClass.CRITICAL);
    assertThat(r.patientLevel()).isEqualTo(VitalClass.CRITICAL);
  }

  // ------------------------------------------------------------------ pulse pressure

  @Test
  void pulsePressureIsInformationOnly() {
    VitalSetResult r = classify("150/60", null);
    assertThat(r.pulsePressure()).isEqualTo(90);
    assertThat(r.reading(VitalType.PULSE_PRESSURE)).isEmpty();
    assertThat(classify(null, "70").pulsePressure()).isNull();
  }

  // ------------------------------------------------------------------ aggregation

  @Test
  void patientLevelIsTheMaximum() {
    List<VitalSign> signs =
        List.of(
            VitalSign.parse(AdultDefaultRules.BP, "118/76"),
            VitalSign.parse(AdultDefaultRules.HR, "72"),
            VitalSign.parse(AdultDefaultRules.RR, "16"),
            VitalSign.parse(AdultDefaultRules.TEMP, "36.8"),
            VitalSign.parse(AdultDefaultRules.SPO2, "98"));
    assertThat(classifier.classify(signs, ADULT).patientLevel()).isEqualTo(VitalClass.NORMAL);

    List<VitalSign> oneBorderline = new ArrayList<>(signs);
    oneBorderline.set(3, VitalSign.parse(AdultDefaultRules.TEMP, "38.0"));
    assertThat(classifier.classify(oneBorderline, ADULT).patientLevel())
        .isEqualTo(VitalClass.BORDERLINE);

    List<VitalSign> oneCritical = new ArrayList<>(oneBorderline);
    oneCritical.set(4, VitalSign.parse(AdultDefaultRules.SPO2, "90"));
    assertThat(classifier.classify(oneCritical, ADULT).patientLevel())
        .isEqualTo(VitalClass.CRITICAL);
  }

  @Test
  void emptySetIsNormal() {
    assertThat(classifier.classify(List.of(), ADULT).patientLevel()).isEqualTo(VitalClass.NORMAL);
  }

  @ParameterizedTest
  @CsvSource({
    "NORMAL,NORMAL,NORMAL",
    "NORMAL,BORDERLINE,BORDERLINE",
    "BORDERLINE,NORMAL,BORDERLINE",
    "BORDERLINE,CRITICAL,CRITICAL",
    "CRITICAL,NORMAL,CRITICAL",
    "CRITICAL,BORDERLINE,CRITICAL",
    "BORDERLINE,BORDERLINE,BORDERLINE",
    "CRITICAL,CRITICAL,CRITICAL"
  })
  void maxOrdering(VitalClass a, VitalClass b, VitalClass expected) {
    assertThat(VitalLevels.max(a, b)).isEqualTo(expected);
    assertThat(VitalLevels.max(List.of(a, b))).isEqualTo(expected);
  }

  // ------------------------------------------------------------------ unknown is never safe

  @Test
  void vitalWithoutAnyRuleIsBorderlineNotNormal() {
    VitalType unknown = VitalType.numeric("LACTATE", "mmol/L");
    VitalSetResult r =
        classifier.classify(List.of(new NumericVital(unknown, new BigDecimal("1.0"))), ADULT);
    VitalReading reading = r.reading("LACTATE").orElseThrow();
    assertThat(reading.level()).isEqualTo(VitalClass.BORDERLINE);
    assertThat(reading.ruleMissing()).isTrue();
    assertThat(r.patientLevel()).isEqualTo(VitalClass.BORDERLINE);
  }

  // ------------------------------------------------------------------ populations

  @Test
  void paediatricWithoutOwnRulesFallsBackToAdultAndFlagsIt() {
    VitalRuleSet paeds = AdultDefaultRules.table().forPopulation(Population.PAEDIATRIC);
    VitalSetResult r =
        classifier.classify(List.of(VitalSign.parse(AdultDefaultRules.HR, "72")), paeds);
    assertThat(r.reading("HR").orElseThrow().level()).isEqualTo(VitalClass.NORMAL);
    assertThat(r.anyFallback()).isTrue();
  }

  @Test
  void populationSpecificRuleWins() {
    List<VitalRuleSpec> specs = new ArrayList<>(AdultDefaultRules.specs());
    specs.add(
        new VitalRuleSpec(
            "HR",
            Population.PAEDIATRIC,
            "/min",
            new BigDecimal("80"),
            new BigDecimal("141"),
            null,
            null,
            new BigDecimal("60"),
            new BigDecimal("180")));
    VitalRuleSet paeds = new VitalRuleTable(specs).forPopulation(Population.PAEDIATRIC);
    VitalSetResult r =
        classifier.classify(List.of(VitalSign.parse(AdultDefaultRules.HR, "135")), paeds);
    assertThat(r.reading("HR").orElseThrow().level()).isEqualTo(VitalClass.NORMAL);
    assertThat(r.anyFallback()).isFalse();
  }

  @Test
  void populationFromPatientData() {
    var today = java.time.LocalDate.of(2026, 11, 7);
    assertThat(Population.of(today.minusYears(30), null, today)).isEqualTo(Population.ADULT);
    assertThat(Population.of(today.minusYears(18), null, today)).isEqualTo(Population.ADULT);
    assertThat(Population.of(today.minusYears(18).plusDays(1), null, today))
        .isEqualTo(Population.PAEDIATRIC);
    assertThat(Population.of(today.minusYears(25), "pregnant", today))
        .isEqualTo(Population.PREGNANCY);
    assertThat(Population.of(null, null, today)).isEqualTo(Population.ADULT);
  }

  // ------------------------------------------------------------------ rule-only vital

  /** Blood glucose exists only as data (type + rule rows); no glucose class in the code. */
  @Test
  void ruleOnlyVitalNeedsNoCode() {
    VitalType glucose =
        new VitalType(
            "GLUCOSE", VitalKind.NUMERIC, "mmol/L", "15074-8", "Blood glucose", "রক্তে গ্লুকোজ",
            new BigDecimal("0.5"), new BigDecimal("50"), 60, true);
    List<VitalRuleSpec> specs = new ArrayList<>(AdultDefaultRules.specs());
    specs.add(
        new VitalRuleSpec(
            "GLUCOSE",
            Population.ADULT,
            "mmol/L",
            new BigDecimal("3.9"),
            new BigDecimal("7.9"),
            new BigDecimal("3.0"),
            new BigDecimal("20.0"),
            new BigDecimal("3.0"),
            new BigDecimal("20.0")));
    VitalRuleSet rules = new VitalRuleTable(specs).forPopulation(Population.ADULT);

    assertThat(VitalSign.parse(glucose, "5.5").classify(rules)).isEqualTo(VitalClass.NORMAL);
    assertThat(VitalSign.parse(glucose, "3.9").classify(rules)).isEqualTo(VitalClass.NORMAL);
    assertThat(VitalSign.parse(glucose, "3.8").classify(rules)).isEqualTo(VitalClass.BORDERLINE);
    assertThat(VitalSign.parse(glucose, "2.9").classify(rules)).isEqualTo(VitalClass.CRITICAL);
    assertThat(VitalSign.parse(glucose, "7.9").classify(rules)).isEqualTo(VitalClass.BORDERLINE);
    assertThat(VitalSign.parse(glucose, "20").classify(rules)).isEqualTo(VitalClass.CRITICAL);
    assertThat(
            classifier
                .classify(List.of(VitalSign.parse(glucose, "25")), rules)
                .patientLevel())
        .isEqualTo(VitalClass.CRITICAL);
  }

  // ------------------------------------------------------------------ rule validation

  @Test
  void seededRulesAreConsistent() {
    for (VitalRuleSpec s : AdultDefaultRules.specs()) {
      assertThat(s.validate()).as(s.vitalType()).isNull();
    }
  }

  @Test
  void inconsistentRulesAreReported() {
    VitalRuleSpec empty =
        new VitalRuleSpec("X", Population.ADULT, "1", null, null, null, null, null, null);
    assertThat(empty.validate()).isNotNull();
    VitalRuleSpec inverted =
        new VitalRuleSpec(
            "X",
            Population.ADULT,
            "1",
            new BigDecimal("10"),
            new BigDecimal("5"),
            null,
            null,
            null,
            null);
    assertThat(inverted.validate()).isNotNull();
    VitalRuleSpec outside =
        new VitalRuleSpec(
            "X",
            Population.ADULT,
            "1",
            new BigDecimal("1"),
            new BigDecimal("5"),
            null,
            null,
            new BigDecimal("2"),
            null);
    assertThat(outside.validate()).isNotNull();
  }

  @Test
  void bpVitalReportsMissingComponentRules() {
    VitalRuleSet noBp = new VitalRuleTable(List.of()).forPopulation(Population.ADULT);
    BloodPressureVital bp = (BloodPressureVital) VitalSign.parse(AdultDefaultRules.BP, "110/70");
    assertThat(bp.hasRules(noBp)).isFalse();
    assertThat(bp.classify(noBp)).isEqualTo(VitalClass.BORDERLINE);
  }
}
