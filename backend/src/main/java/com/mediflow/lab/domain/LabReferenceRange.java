package com.mediflow.lab.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** One analyte of a lab test with its reference and critical range (LOINC code, UCUM unit). */
@Entity
@Table(name = "lab_reference_range")
public class LabReferenceRange extends BaseEntity {

  @Column(name = "test_id", nullable = false)
  private UUID testId;

  @Column(name = "analyte_code", nullable = false)
  private String analyteCode;

  @Column(name = "analyte_name", nullable = false)
  private String analyteName;

  @Column(name = "loinc_code")
  private String loincCode;

  @Column(name = "ucum_unit")
  private String ucumUnit;

  /** {@code null} = any sex, otherwise "M" or "F". */
  @Column(name = "sex", length = 1)
  private String sex;

  @Column(name = "ref_low")
  private BigDecimal refLow;

  @Column(name = "ref_high")
  private BigDecimal refHigh;

  @Column(name = "critical_low")
  private BigDecimal criticalLow;

  @Column(name = "critical_high")
  private BigDecimal criticalHigh;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  public UUID getTestId() {
    return testId;
  }

  public void setTestId(UUID testId) {
    this.testId = testId;
  }

  public String getAnalyteCode() {
    return analyteCode;
  }

  public void setAnalyteCode(String analyteCode) {
    this.analyteCode = analyteCode;
  }

  public String getAnalyteName() {
    return analyteName;
  }

  public void setAnalyteName(String analyteName) {
    this.analyteName = analyteName;
  }

  public String getLoincCode() {
    return loincCode;
  }

  public void setLoincCode(String loincCode) {
    this.loincCode = loincCode;
  }

  public String getUcumUnit() {
    return ucumUnit;
  }

  public void setUcumUnit(String ucumUnit) {
    this.ucumUnit = ucumUnit;
  }

  public String getSex() {
    return sex;
  }

  public void setSex(String sex) {
    this.sex = sex;
  }

  public BigDecimal getRefLow() {
    return refLow;
  }

  public void setRefLow(BigDecimal refLow) {
    this.refLow = refLow;
  }

  public BigDecimal getRefHigh() {
    return refHigh;
  }

  public void setRefHigh(BigDecimal refHigh) {
    this.refHigh = refHigh;
  }

  public BigDecimal getCriticalLow() {
    return criticalLow;
  }

  public void setCriticalLow(BigDecimal criticalLow) {
    this.criticalLow = criticalLow;
  }

  public BigDecimal getCriticalHigh() {
    return criticalHigh;
  }

  public void setCriticalHigh(BigDecimal criticalHigh) {
    this.criticalHigh = criticalHigh;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
