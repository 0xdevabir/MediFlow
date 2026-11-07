package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "drug_dose_limit")
public class DrugDoseLimit extends BaseEntity {

  @Column(name = "generic_id", nullable = false)
  private UUID genericId;

  @Column(name = "population", nullable = false)
  private String population;

  @Column(name = "route")
  private String route;

  @Column(name = "min_dose")
  private BigDecimal minDose;

  @Column(name = "max_dose")
  private BigDecimal maxDose;

  @Column(name = "unit")
  private String unit;

  public UUID getGenericId() {
    return genericId;
  }

  public void setGenericId(UUID genericId) {
    this.genericId = genericId;
  }

  public String getPopulation() {
    return population;
  }

  public void setPopulation(String population) {
    this.population = population;
  }

  public String getRoute() {
    return route;
  }

  public void setRoute(String route) {
    this.route = route;
  }

  public BigDecimal getMinDose() {
    return minDose;
  }

  public void setMinDose(BigDecimal minDose) {
    this.minDose = minDose;
  }

  public BigDecimal getMaxDose() {
    return maxDose;
  }

  public void setMaxDose(BigDecimal maxDose) {
    this.maxDose = maxDose;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

}
