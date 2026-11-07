package com.mediflow.clinical.vitals.domain;

import java.time.LocalDate;
import java.time.Period;

/** {@code vital_rule.population}. Paediatric and pregnancy tables are separate (R&D v1.3 §11). */
public enum Population {
  ADULT,
  PAEDIATRIC,
  PREGNANCY;

  public static final int ADULT_AGE = 18;

  /**
   * Pregnancy takes precedence, then age below {@value #ADULT_AGE} means paediatric; an unknown date
   * of birth is treated as adult.
   */
  public static Population of(LocalDate dateOfBirth, String pregnancyStatus, LocalDate today) {
    if (pregnancyStatus != null && pregnancyStatus.trim().equalsIgnoreCase("PREGNANT")) {
      return PREGNANCY;
    }
    if (dateOfBirth != null && Period.between(dateOfBirth, today).getYears() < ADULT_AGE) {
      return PAEDIATRIC;
    }
    return ADULT;
  }
}
