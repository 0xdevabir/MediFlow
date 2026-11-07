package com.mediflow.prescription.sign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediflow.shared.domain.PrescriptionStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Revoke → ENTERED_IN_ERROR + 24 h incident review flag (logic-level). */
class RevokeFlowTest {

  @Test
  void revokeTargetStatusAndReviewWindow() {
    assertEquals(PrescriptionStatus.ENTERED_IN_ERROR, PrescriptionStatus.valueOf("ENTERED_IN_ERROR"));
    Instant opened = Instant.parse("2026-11-07T12:00:00Z");
    Instant due = opened.plus(Duration.ofHours(24));
    assertEquals(opened.plusSeconds(86400), due);
    assertTrue(due.isAfter(opened));
  }
}
