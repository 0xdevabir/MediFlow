package com.mediflow.lab.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** One structured result value of a diagnostic report, with the range it was judged against. */
@Entity
@Table(name = "lab_result_value")
public class LabResultValue extends BaseEntity {

  @Column(name = "report_id", nullable = false)
  private UUID reportId;

  @Column(name = "test_id")
  private UUID testId;

  @Column(name = "analyte_code", nullable = false)
  private String analyteCode;

  @Column(name = "analyte_name", nullable = false)
  private String analyteName;

  @Column(name = "loinc_code")
  private String loincCode;

  @Column(name = "ucum_unit")
  private String ucumUnit;

  @Column(name = "value_numeric")
  private BigDecimal valueNumeric;

  @Column(name = "value_text")
  private String valueText;

  @Column(name = "ref_low")
  private BigDecimal refLow;

  @Column(name = "ref_high")
  private BigDecimal refHigh;

  @Column(name = "critical_low")
  private BigDecimal criticalLow;

  @Column(name = "critical_high")
  private BigDecimal criticalHigh;

  @Enumerated(EnumType.STRING)
  @Column(name = "flag", nullable = false)
  private ResultFlag flag;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  public UUID getReportId() {
    return reportId;
  }

  public void setReportId(UUID reportId) {
    this.reportId = reportId;
  }

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

  public BigDecimal getValueNumeric() {
    return valueNumeric;
  }

  public void setValueNumeric(BigDecimal valueNumeric) {
    this.valueNumeric = valueNumeric;
  }

  public String getValueText() {
    return valueText;
  }

  public void setValueText(String valueText) {
    this.valueText = valueText;
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

  public ResultFlag getFlag() {
    return flag;
  }

  public void setFlag(ResultFlag flag) {
    this.flag = flag;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
