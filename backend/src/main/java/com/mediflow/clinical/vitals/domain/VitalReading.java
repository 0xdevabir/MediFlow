package com.mediflow.clinical.vitals.domain;

import com.mediflow.shared.domain.VitalClass;

/**
 * A classified vital.
 *
 * @param derived computed (MAP, shock index) rather than entered
 * @param ruleMissing no rule existed, so the level is a safety BORDERLINE
 * @param adultFallback the ADULT rule was used because the population has none
 */
public record VitalReading(
    VitalSign sign,
    VitalClass level,
    boolean derived,
    boolean ruleMissing,
    boolean adultFallback) {

  public String code() {
    return sign.type().code();
  }
}
