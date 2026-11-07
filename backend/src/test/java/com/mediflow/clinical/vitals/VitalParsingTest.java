package com.mediflow.clinical.vitals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediflow.clinical.vitals.domain.BloodPressureVital;
import com.mediflow.clinical.vitals.domain.NumericVital;
import com.mediflow.clinical.vitals.domain.VitalParseException;
import com.mediflow.clinical.vitals.domain.VitalSign;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class VitalParsingTest {

  @Test
  void parseDispatchesOnKind() {
    assertThat(VitalSign.parse(AdultDefaultRules.BP, "120/80"))
        .isInstanceOf(BloodPressureVital.class);
    assertThat(VitalSign.parse(AdultDefaultRules.HR, "72")).isInstanceOf(NumericVital.class);
  }

  @ParameterizedTest
  @CsvSource({"'120/80',120,80", "' 120 / 80 ',120,80", "'90/60',90,60", "'200/110',200,110"})
  void bloodPressureParses(String raw, int sys, int dia) {
    BloodPressureVital bp = (BloodPressureVital) VitalSign.parse(AdultDefaultRules.BP, raw);
    assertThat(bp.systolic()).isEqualTo(sys);
    assertThat(bp.diastolic()).isEqualTo(dia);
    assertThat(bp.textValue()).isEqualTo(sys + "/" + dia);
    assertThat(bp.numericValue()).isEqualByComparingTo(BigDecimal.valueOf(sys));
    assertThat(bp.pulsePressure()).isEqualTo(sys - dia);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void bloodPressureEmpty(String raw) {
    assertError(raw, VitalParseException.EMPTY);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "120", "120/", "/80", "120-80", "120\\80", "abc", "120/80/60", "12O/80", "120.5/80",
        "-120/80", "120/ 8 0", "1200/80", "120/80mmHg"
      })
  void bloodPressureBadFormat(String raw) {
    assertError(raw, VitalParseException.BP_FORMAT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"80/80", "80/90", "60/120"})
  void bloodPressureSystolicMustExceedDiastolic(String raw) {
    assertError(raw, VitalParseException.BP_ORDER);
  }

  @ParameterizedTest
  @ValueSource(strings = {"39/20", "301/80", "120/19", "250/201", "999/99"})
  void bloodPressureImplausible(String raw) {
    assertError(raw, VitalParseException.BP_RANGE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"40/20", "300/200"})
  void bloodPressureGuardEdgesAccepted(String raw) {
    assertThat(VitalSign.parse(AdultDefaultRules.BP, raw)).isInstanceOf(BloodPressureVital.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "7 2", "72bpm", "--1", "1e"})
  void numericNotANumber(String raw) {
    assertThatThrownBy(() -> VitalSign.parse(AdultDefaultRules.HR, raw))
        .isInstanceOfSatisfying(
            VitalParseException.class,
            e -> {
              assertThat(e.messageKey()).isEqualTo(VitalParseException.NOT_A_NUMBER);
              assertThat(e.vitalCode()).isEqualTo("HR");
            });
  }

  @ParameterizedTest
  @NullAndEmptySource
  void numericEmpty(String raw) {
    assertThatThrownBy(() -> VitalSign.parse(AdultDefaultRules.HR, raw))
        .isInstanceOfSatisfying(
            VitalParseException.class,
            e -> assertThat(e.messageKey()).isEqualTo(VitalParseException.EMPTY));
  }

  @ParameterizedTest
  @CsvSource({"HR,19", "HR,301", "SPO2,101", "SPO2,49", "TEMP,46", "TEMP,24.9", "RR,0", "RR,81"})
  void numericImplausible(String code, String raw) {
    var type =
        switch (code) {
          case "HR" -> AdultDefaultRules.HR;
          case "SPO2" -> AdultDefaultRules.SPO2;
          case "TEMP" -> AdultDefaultRules.TEMP;
          default -> AdultDefaultRules.RR;
        };
    assertThatThrownBy(() -> VitalSign.parse(type, raw))
        .isInstanceOfSatisfying(
            VitalParseException.class,
            e -> assertThat(e.messageKey()).isEqualTo(VitalParseException.OUT_OF_RANGE));
  }

  private static void assertError(String raw, String key) {
    assertThatThrownBy(() -> VitalSign.parse(AdultDefaultRules.BP, raw))
        .isInstanceOfSatisfying(
            VitalParseException.class,
            e -> {
              assertThat(e.messageKey()).isEqualTo(key);
              assertThat(e.vitalCode()).isEqualTo("BP");
            });
  }
}
