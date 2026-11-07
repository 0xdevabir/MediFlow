package com.mediflow.clinical.vitals.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "clinical_vital_definition")
public class VitalDefinitionEntity extends BaseEntity {

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false)
  private VitalKind kind;

  @Column(name = "ucum_unit", nullable = false)
  private String ucumUnit;

  @Column(name = "loinc_code")
  private String loincCode;

  @Column(name = "display_en", nullable = false)
  private String displayEn;

  @Column(name = "display_bn")
  private String displayBn;

  @Column(name = "plausible_min")
  private BigDecimal plausibleMin;

  @Column(name = "plausible_max")
  private BigDecimal plausibleMax;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "nurse_entry", nullable = false)
  private boolean nurseEntry;

  @Column(name = "active", nullable = false)
  private boolean active;

  public VitalType toVitalType() {
    return new VitalType(
        code,
        kind,
        ucumUnit,
        loincCode,
        displayEn,
        displayBn,
        plausibleMin,
        plausibleMax,
        sortOrder,
        nurseEntry);
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public VitalKind getKind() {
    return kind;
  }

  public void setKind(VitalKind kind) {
    this.kind = kind;
  }

  public String getUcumUnit() {
    return ucumUnit;
  }

  public void setUcumUnit(String ucumUnit) {
    this.ucumUnit = ucumUnit;
  }

  public String getLoincCode() {
    return loincCode;
  }

  public void setLoincCode(String loincCode) {
    this.loincCode = loincCode;
  }

  public String getDisplayEn() {
    return displayEn;
  }

  public void setDisplayEn(String displayEn) {
    this.displayEn = displayEn;
  }

  public String getDisplayBn() {
    return displayBn;
  }

  public void setDisplayBn(String displayBn) {
    this.displayBn = displayBn;
  }

  public BigDecimal getPlausibleMin() {
    return plausibleMin;
  }

  public void setPlausibleMin(BigDecimal plausibleMin) {
    this.plausibleMin = plausibleMin;
  }

  public BigDecimal getPlausibleMax() {
    return plausibleMax;
  }

  public void setPlausibleMax(BigDecimal plausibleMax) {
    this.plausibleMax = plausibleMax;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public boolean isNurseEntry() {
    return nurseEntry;
  }

  public void setNurseEntry(boolean nurseEntry) {
    this.nurseEntry = nurseEntry;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
