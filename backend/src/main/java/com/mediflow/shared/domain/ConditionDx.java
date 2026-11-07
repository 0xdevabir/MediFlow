package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "condition_dx")
public class ConditionDx extends BaseEntity {

  @Column(name = "encounter_id", nullable = false)
  private UUID encounterId;

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "icd10_code", nullable = false)
  private String icd10Code;

  @Column(name = "display", nullable = false)
  private String display;

  @Column(name = "clinical_status", nullable = false)
  private String clinicalStatus;

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

  public String getIcd10Code() {
    return icd10Code;
  }

  public void setIcd10Code(String icd10Code) {
    this.icd10Code = icd10Code;
  }

  public String getDisplay() {
    return display;
  }

  public void setDisplay(String display) {
    this.display = display;
  }

  public String getClinicalStatus() {
    return clinicalStatus;
  }

  public void setClinicalStatus(String clinicalStatus) {
    this.clinicalStatus = clinicalStatus;
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
