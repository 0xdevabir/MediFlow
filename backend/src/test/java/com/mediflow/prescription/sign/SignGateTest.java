package com.mediflow.prescription.sign;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediflow.prescription.domain.CheckType;
import com.mediflow.prescription.domain.GateStatus;
import com.mediflow.prescription.safety.ConfidenceGate;
import com.mediflow.prescription.safety.SafetyEngine;
import com.mediflow.prescription.safety.SafetyFinding;
import com.mediflow.shared.domain.ConflictSeverity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Signing is impossible with unresolved CRITICAL / NEEDS_REVIEW / UNKNOWN (PLAN §6). */
class SignGateTest {

  @Test
  void unresolvedCriticalBlocksSign() {
    UUID item = UUID.randomUUID();
    SafetyFinding allergy =
        SafetyFinding.of(
            item,
            CheckType.ALLERGY,
            ConflictSeverity.CRITICAL,
            "a",
            "rx.safety.allergy",
            "pen",
            false);
    assertTrue(SafetyEngine.hasUnresolvedCritical(List.of(allergy), Set.of()));
  }

  @Test
  void needsReviewAndUnknownRequireResolve() {
    assertTrue(
        GateStatus.NEEDS_REVIEW
            == ConfidenceGate.decide(
                new BigDecimal("0.5"),
                new BigDecimal("0.9"),
                new BigDecimal("0.9"),
                true,
                null,
                ConfidenceGate.DEFAULT_THRESHOLD));
    assertTrue(
        GateStatus.UNKNOWN
            == ConfidenceGate.decide(
                new BigDecimal("0.99"),
                new BigDecimal("0.99"),
                new BigDecimal("0.99"),
                false,
                null,
                ConfidenceGate.DEFAULT_THRESHOLD));
  }

  @Test
  void overridableCriticalClearedOnlyWhenAcked() {
    SafetyFinding ix =
        SafetyFinding.of(
            UUID.randomUUID(),
            CheckType.INTERACTION,
            ConflictSeverity.CRITICAL,
            "ix",
            "rx.safety.interaction",
            "bleed",
            true);
    assertTrue(SafetyEngine.hasUnresolvedCritical(List.of(ix), Set.of()));
    assertFalse(SafetyEngine.hasUnresolvedCritical(List.of(ix), Set.of("ix")));
  }
}
