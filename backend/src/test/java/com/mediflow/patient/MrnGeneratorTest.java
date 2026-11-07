package com.mediflow.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediflow.patient.service.MrnGenerator;
import org.junit.jupiter.api.Test;

class MrnGeneratorTest {

  @Test
  void formatPadsYearAndSequence() {
    assertThat(MrnGenerator.format(2026, 1)).isEqualTo("MF-2026-000001");
    assertThat(MrnGenerator.format(2026, 42)).isEqualTo("MF-2026-000042");
  }

  @Test
  void formatRejectsOverflow() {
    assertThatThrownBy(() -> MrnGenerator.format(2026, 1_000_000L))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void looksLikeMrn() {
    assertThat(MrnGenerator.looksLikeMrn("MF-2026-000001")).isTrue();
    assertThat(MrnGenerator.looksLikeMrn("mf-2026-000001")).isTrue();
    assertThat(MrnGenerator.looksLikeMrn("1990123456789")).isFalse();
  }
}
