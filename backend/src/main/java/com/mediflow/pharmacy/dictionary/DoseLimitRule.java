package com.mediflow.pharmacy.dictionary;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * population ADULT | PAEDIATRIC; unit {@code mg/dose, mg/day, mg/kg/dose, mg/kg/day} (or mcg);
 * bounds inclusive, null = unbounded. {@code placeholder} = needs a licensed source.
 */
public record DoseLimitRule(
    UUID id,
    UUID genericId,
    String population,
    String route,
    BigDecimal minDose,
    BigDecimal maxDose,
    String unit,
    boolean placeholder)
    implements Serializable {

  public boolean perKg() {
    return unit != null && unit.contains("/kg/");
  }

  public boolean perDay() {
    return unit != null && unit.endsWith("/day");
  }
}
