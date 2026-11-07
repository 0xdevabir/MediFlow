package com.mediflow.clinical.vitals.domain;

import com.mediflow.shared.domain.VitalClass;
import java.util.List;
import java.util.Optional;

/**
 * One classified vital set.
 *
 * @param pulsePressure SBP − DBP in mmHg, information only ({@code null} without BP)
 * @param patientLevel highest level among all readings, derived ones included
 */
public record VitalSetResult(
    Population population,
    List<VitalReading> readings,
    Integer pulsePressure,
    VitalClass patientLevel) {

  public VitalSetResult {
    readings = List.copyOf(readings);
  }

  public Optional<VitalReading> reading(String code) {
    return readings.stream().filter(r -> r.code().equals(code)).findFirst();
  }

  public boolean anyFallback() {
    return readings.stream().anyMatch(VitalReading::adultFallback);
  }

  public boolean anyRuleMissing() {
    return readings.stream().anyMatch(VitalReading::ruleMissing);
  }
}
