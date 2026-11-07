package com.mediflow.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediflow.lab.domain.LabReferenceRange;
import com.mediflow.lab.domain.ResultFlag;
import com.mediflow.lab.service.ResultFlagCalculator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultFlagCalculatorTest {

  private static BigDecimal d(String v) {
    return v == null ? null : new BigDecimal(v);
  }

  private static ResultFlag glucose(String v) {
    return ResultFlagCalculator.flag(d(v), d("3.9"), d("5.5"), d("2.8"), d("22.2"));
  }

  @Test
  void flagsAgainstReferenceAndCriticalLimits() {
    assertThat(glucose("5.0")).isEqualTo(ResultFlag.NORMAL);
    assertThat(glucose("3.9")).isEqualTo(ResultFlag.NORMAL);
    assertThat(glucose("5.5")).isEqualTo(ResultFlag.NORMAL);
    assertThat(glucose("3.5")).isEqualTo(ResultFlag.LOW);
    assertThat(glucose("8.1")).isEqualTo(ResultFlag.HIGH);
    assertThat(glucose("2.8")).isEqualTo(ResultFlag.CRITICAL_LOW);
    assertThat(glucose("1.9")).isEqualTo(ResultFlag.CRITICAL_LOW);
    assertThat(glucose("22.2")).isEqualTo(ResultFlag.CRITICAL_HIGH);
    assertThat(glucose("30")).isEqualTo(ResultFlag.CRITICAL_HIGH);
  }

  @Test
  void openEndedRanges() {
    // HDL: only a lower limit
    assertThat(ResultFlagCalculator.flag(d("35"), d("40"), null, null, null))
        .isEqualTo(ResultFlag.LOW);
    assertThat(ResultFlagCalculator.flag(d("90"), d("40"), null, null, null))
        .isEqualTo(ResultFlag.NORMAL);
    // Triglycerides: upper + critical high only
    assertThat(ResultFlagCalculator.flag(d("1200"), null, d("150"), null, d("1000")))
        .isEqualTo(ResultFlag.CRITICAL_HIGH);
  }

  @Test
  void missingValueIsRejected() {
    assertThatThrownBy(() -> glucose(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void picksSexSpecificRangeThenNeutral() {
    LabReferenceRange male = range("M", "13");
    LabReferenceRange female = range("F", "12");
    LabReferenceRange any = range(null, "12.5");
    List<LabReferenceRange> all = List.of(any, male, female);
    assertThat(ResultFlagCalculator.pick(all, "male")).containsSame(male);
    assertThat(ResultFlagCalculator.pick(all, "F")).containsSame(female);
    assertThat(ResultFlagCalculator.pick(all, null)).containsSame(any);
    assertThat(ResultFlagCalculator.pick(List.of(male), "F")).containsSame(male);
  }

  @Test
  void aggregatesFlags() {
    assertThat(ResultFlagCalculator.anyAbnormal(List.of(ResultFlag.NORMAL, ResultFlag.HIGH))).isTrue();
    assertThat(ResultFlagCalculator.anyCritical(List.of(ResultFlag.NORMAL, ResultFlag.HIGH)))
        .isFalse();
    assertThat(ResultFlagCalculator.anyCritical(List.of(ResultFlag.CRITICAL_LOW))).isTrue();
    assertThat(ResultFlagCalculator.anyAbnormal(List.of(ResultFlag.NORMAL))).isFalse();
  }

  private static LabReferenceRange range(String sex, String low) {
    LabReferenceRange r = new LabReferenceRange();
    r.setSex(sex);
    r.setRefLow(d(low));
    return r;
  }
}
