package com.mediflow.prescription.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediflow.prescription.domain.GateStatus;
import com.mediflow.shared.domain.ConflictSeverity;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Every row of the PDF / DOCX "85 % rule" / Table 20 safety gate. */
class ConfidenceGateTest {

  private static final BigDecimal T = ConfidenceGate.DEFAULT_THRESHOLD;

  static Stream<Arguments> table20() {
    BigDecimal high = new BigDecimal("0.90");
    BigDecimal low = new BigDecimal("0.70");
    return Stream.of(
        // S1 Confidence gate — overall < 85 % → NEEDS_REVIEW (manual path for that item)
        Arguments.of("S1 below threshold", high, low, high, true, null, GateStatus.NEEDS_REVIEW),
        // S2 Pass — ≥ 85 % and no critical → PREFILLED
        Arguments.of("S2 pass", high, high, high, true, null, GateStatus.PREFILLED),
        Arguments.of(
            "S2 pass with warning", high, high, high, true, ConflictSeverity.WARNING, GateStatus.PREFILLED),
        // S3 Critical conflict at any confidence → HARD_STOP
        Arguments.of(
            "S3 critical high conf",
            high,
            high,
            high,
            true,
            ConflictSeverity.CRITICAL,
            GateStatus.HARD_STOP),
        Arguments.of(
            "S3 critical low conf",
            low,
            low,
            low,
            true,
            ConflictSeverity.CRITICAL,
            GateStatus.HARD_STOP),
        // S4 Unknown item → UNKNOWN (treated as < 85 %)
        Arguments.of("S4 unknown brand", high, high, high, false, null, GateStatus.UNKNOWN),
        Arguments.of(
            "S4 unknown even with critical stays UNKNOWN before severity? critical wins",
            high,
            high,
            high,
            false,
            ConflictSeverity.CRITICAL,
            GateStatus.HARD_STOP));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("table20")
  void table20_rows(
      String name,
      BigDecimal drug,
      BigDecimal dose,
      BigDecimal freq,
      boolean known,
      ConflictSeverity severity,
      GateStatus expected) {
    assertEquals(
        expected, ConfidenceGate.decide(drug, dose, freq, known, severity, T), name);
  }

  @Test
  void itemConfidence_isMinOfThree() {
    assertEquals(
        new BigDecimal("0.70"),
        ConfidenceGate.itemConfidence(
            new BigDecimal("0.95"), new BigDecimal("0.70"), new BigDecimal("0.90")));
  }

  @Test
  void itemConfidence_nullsCountAsZero() {
    assertEquals(0, ConfidenceGate.itemConfidence(new BigDecimal("0.99"), null, new BigDecimal("0.99")).compareTo(BigDecimal.ZERO));
  }

  @Test
  void meetsThreshold_boundary() {
    assertTrue(ConfidenceGate.meetsThreshold(new BigDecimal("0.85"), T));
    assertFalse(ConfidenceGate.meetsThreshold(new BigDecimal("0.8499"), T));
  }

  @Test
  void forManual_criticalBecomesHardStop() {
    assertEquals(GateStatus.HARD_STOP, ConfidenceGate.forManual(ConflictSeverity.CRITICAL));
    assertEquals(GateStatus.MANUAL, ConfidenceGate.forManual(null));
    assertEquals(GateStatus.MANUAL, ConfidenceGate.forManual(ConflictSeverity.WARNING));
  }

  @Test
  void parseThreshold_defaultsAndClamps() {
    assertEquals(0, ConfidenceGate.parseThreshold(null).compareTo(T));
    assertEquals(0, ConfidenceGate.parseThreshold("nope").compareTo(T));
    assertEquals(0, ConfidenceGate.parseThreshold("1.5").compareTo(T));
    assertEquals(0, ConfidenceGate.parseThreshold("0.90").compareTo(new BigDecimal("0.9000")));
  }
}
