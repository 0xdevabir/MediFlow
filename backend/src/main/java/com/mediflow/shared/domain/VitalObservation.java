package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vital_observation")
public class VitalObservation extends BaseEntity {

  @Column(name = "encounter_id", nullable = false)
  private UUID encounterId;

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "vital_type", nullable = false)
  private String vitalType;

  @Column(name = "value_numeric")
  private BigDecimal valueNumeric;

  @Column(name = "value_text")
  private String valueText;

  @Column(name = "unit")
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(name = "classification", nullable = false)
  private VitalClass classification;

  @Column(name = "recorded_by")
  private UUID recordedBy;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  public UUID getEncounterId() {
    return encounterId;
  }

  public void setEncounterId(UUID encounterId) {
    this.encounterId = encounterId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public String getVitalType() {
    return vitalType;
  }

  public void setVitalType(String vitalType) {
    this.vitalType = vitalType;
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

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  public VitalClass getClassification() {
    return classification;
  }

  public void setClassification(VitalClass classification) {
    this.classification = classification;
  }

  public UUID getRecordedBy() {
    return recordedBy;
  }

  public void setRecordedBy(UUID recordedBy) {
    this.recordedBy = recordedBy;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public void setRecordedAt(Instant recordedAt) {
    this.recordedAt = recordedAt;
  }

}
