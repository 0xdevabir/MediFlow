package com.mediflow.clinical.vitals;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.clinical.vitals.web.TrendSvg;
import com.mediflow.shared.domain.VitalClass;
import com.mediflow.shared.domain.VitalObservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrendSvgTest {

  @Test
  void emptyIsBlank() {
    assertThat(TrendSvg.polyline(List.of(), 100, 50)).isEmpty();
  }

  @Test
  void buildsPoints() {
    VitalObservation a = obs("10");
    VitalObservation b = obs("20");
    String pts = TrendSvg.polyline(List.of(a, b), 100, 50);
    assertThat(pts).contains(",");
    assertThat(pts.split(" ")).hasSize(2);
  }

  private static VitalObservation obs(String v) {
    VitalObservation o = new VitalObservation();
    o.setEncounterId(UUID.randomUUID());
    o.setPatientId(UUID.randomUUID());
    o.setVitalType("HR");
    o.setValueNumeric(new BigDecimal(v));
    o.setClassification(VitalClass.NORMAL);
    o.setRecordedAt(Instant.parse("2026-11-07T00:00:00Z"));
    return o;
  }
}
