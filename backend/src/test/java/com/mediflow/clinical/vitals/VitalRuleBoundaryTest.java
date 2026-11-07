package com.mediflow.clinical.vitals;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.clinical.vitals.domain.Population;
import com.mediflow.clinical.vitals.domain.VitalRuleSet;
import com.mediflow.clinical.vitals.domain.VitalSign;
import com.mediflow.clinical.vitals.domain.VitalType;
import com.mediflow.shared.domain.VitalClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Every edge of the adult rule table (R&D v1.3 §11), on both sides of each boundary. */
class VitalRuleBoundaryTest {

  private static final VitalRuleSet RULES =
      AdultDefaultRules.table().forPopulation(Population.ADULT);

  private static VitalClass classify(VitalType type, String raw) {
    return VitalSign.parse(type, raw).classify(RULES);
  }

  @ParameterizedTest(name = "HR {0} → {1}")
  @CsvSource({
    "20, CRITICAL", "38, CRITICAL", "39, CRITICAL", "39.9, CRITICAL",
    "40, BORDERLINE", "45, BORDERLINE", "49, BORDERLINE",
    "50, BORDERLINE", "59, BORDERLINE", "59.9, BORDERLINE",
    "60, NORMAL", "72, NORMAL", "100, NORMAL", "100.4, NORMAL",
    "101, BORDERLINE", "129, BORDERLINE", "129.9, BORDERLINE",
    "130, CRITICAL", "131, CRITICAL", "300, CRITICAL"
  })
  void heartRate(String raw, VitalClass expected) {
    assertThat(classify(AdultDefaultRules.HR, raw)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "RR {0} → {1}")
  @CsvSource({
    "1, CRITICAL", "7, CRITICAL", "8, CRITICAL",
    "9, BORDERLINE", "10, BORDERLINE", "11, BORDERLINE",
    "12, NORMAL", "16, NORMAL", "20, NORMAL",
    "21, BORDERLINE", "24, BORDERLINE",
    "25, CRITICAL", "26, CRITICAL", "80, CRITICAL"
  })
  void respiratoryRate(String raw, VitalClass expected) {
    assertThat(classify(AdultDefaultRules.RR, raw)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "TEMP {0} → {1}")
  @CsvSource({
    "34.0, CRITICAL", "34.9, CRITICAL", "35.0, CRITICAL",
    "35.1, BORDERLINE", "35.5, BORDERLINE", "36.0, BORDERLINE",
    "36.1, NORMAL", "36.8, NORMAL", "37.2, NORMAL",
    "37.3, BORDERLINE", "38.5, BORDERLINE", "39.4, BORDERLINE",
    "39.5, CRITICAL", "39.6, CRITICAL", "41.0, CRITICAL",
    "'36,8', NORMAL"
  })
  void temperature(String raw, VitalClass expected) {
    assertThat(classify(AdultDefaultRules.TEMP, raw)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "SpO2 {0} → {1}")
  @CsvSource({
    "50, CRITICAL", "85, CRITICAL", "89, CRITICAL", "90, CRITICAL",
    "91, BORDERLINE", "92, BORDERLINE", "94, BORDERLINE",
    "95, NORMAL", "98, NORMAL", "100, NORMAL"
  })
  void spo2(String raw, VitalClass expected) {
    assertThat(classify(AdultDefaultRules.SPO2, raw)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "BP {0} → {1}")
  @CsvSource({
    // normal: < 120 / < 80
    "'110/70', NORMAL", "'119/79', NORMAL", "'90/60', NORMAL",
    // systolic edges
    "'120/70', BORDERLINE", "'150/70', BORDERLINE", "'179/79', BORDERLINE",
    "'180/79', CRITICAL", "'181/70', CRITICAL", "'220/100', CRITICAL",
    "'89/60', CRITICAL", "'90/60', NORMAL", "'85/50', CRITICAL",
    // diastolic edges
    "'110/80', BORDERLINE", "'115/100', BORDERLINE", "'150/119', BORDERLINE",
    "'150/120', CRITICAL", "'160/121', CRITICAL",
    // combined: the higher component wins
    "'179/119', BORDERLINE", "'180/119', CRITICAL", "'179/120', CRITICAL",
    "'89/80', CRITICAL", "'119/80', BORDERLINE", "'120/79', BORDERLINE"
  })
  void bloodPressure(String raw, VitalClass expected) {
    assertThat(classify(AdultDefaultRules.BP, raw)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "systolic {0} → {1}")
  @CsvSource({
    "40, CRITICAL", "89, CRITICAL", "90, NORMAL", "119, NORMAL", "120, BORDERLINE",
    "179, BORDERLINE", "180, CRITICAL", "300, CRITICAL"
  })
  void systolicComponentAlone(int sys, VitalClass expected) {
    assertThat(RULES.classify(VitalType.BP_SYSTOLIC, java.math.BigDecimal.valueOf(sys)))
        .isEqualTo(expected);
  }

  @ParameterizedTest(name = "diastolic {0} → {1}")
  @CsvSource({
    "20, NORMAL", "79, NORMAL", "80, BORDERLINE", "119, BORDERLINE", "120, CRITICAL",
    "200, CRITICAL"
  })
  void diastolicComponentAlone(int dia, VitalClass expected) {
    assertThat(RULES.classify(VitalType.BP_DIASTOLIC, java.math.BigDecimal.valueOf(dia)))
        .isEqualTo(expected);
  }
}
