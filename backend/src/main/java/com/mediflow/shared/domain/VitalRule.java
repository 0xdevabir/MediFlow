package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "vital_rule")
public class VitalRule extends BaseEntity {

  @Column(name = "vital_type", nullable = false)
  private String vitalType;

  @Column(name = "population", nullable = false)
  private String population;

  @Column(name = "unit", nullable = false)
  private String unit;

  @Column(name = "normal_min")
  private BigDecimal normalMin;

  @Column(name = "normal_max")
  private BigDecimal normalMax;

  @Column(name = "borderline_min")
  private BigDecimal borderlineMin;

  @Column(name = "borderline_max")
  private BigDecimal borderlineMax;

  @Column(name = "critical_min")
  private BigDecimal criticalMin;

  @Column(name = "critical_max")
  private BigDecimal criticalMax;

  @Column(name = "active", nullable = false)
  private boolean active;

  public String getVitalType() {
    return vitalType;
  }

  public void setVitalType(String vitalType) {
    this.vitalType = vitalType;
  }

  public String getPopulation() {
    return population;
  }

  public void setPopulation(String population) {
    this.population = population;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  public BigDecimal getNormalMin() {
    return normalMin;
  }

  public void setNormalMin(BigDecimal normalMin) {
    this.normalMin = normalMin;
  }

  public BigDecimal getNormalMax() {
    return normalMax;
  }

  public void setNormalMax(BigDecimal normalMax) {
    this.normalMax = normalMax;
  }

  public BigDecimal getBorderlineMin() {
    return borderlineMin;
  }

  public void setBorderlineMin(BigDecimal borderlineMin) {
    this.borderlineMin = borderlineMin;
  }

  public BigDecimal getBorderlineMax() {
    return borderlineMax;
  }

  public void setBorderlineMax(BigDecimal borderlineMax) {
    this.borderlineMax = borderlineMax;
  }

  public BigDecimal getCriticalMin() {
    return criticalMin;
  }

  public void setCriticalMin(BigDecimal criticalMin) {
    this.criticalMin = criticalMin;
  }

  public BigDecimal getCriticalMax() {
    return criticalMax;
  }

  public void setCriticalMax(BigDecimal criticalMax) {
    this.criticalMax = criticalMax;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

}
