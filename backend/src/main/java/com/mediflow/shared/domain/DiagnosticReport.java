package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagnostic_report")
public class DiagnosticReport extends BaseEntity {

  @Column(name = "lab_order_id", nullable = false, unique = true)
  private UUID labOrderId;

  @Column(name = "file_ref")
  private String fileRef;

  @Column(name = "verified_by")
  private UUID verifiedBy;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "abnormal_flag", nullable = false)
  private boolean abnormalFlag;

  @Column(name = "critical_flag", nullable = false)
  private boolean criticalFlag;

  @Column(name = "summary", columnDefinition = "TEXT")
  private String summary;

  public UUID getLabOrderId() {
    return labOrderId;
  }

  public void setLabOrderId(UUID labOrderId) {
    this.labOrderId = labOrderId;
  }

  public String getFileRef() {
    return fileRef;
  }

  public void setFileRef(String fileRef) {
    this.fileRef = fileRef;
  }

  public UUID getVerifiedBy() {
    return verifiedBy;
  }

  public void setVerifiedBy(UUID verifiedBy) {
    this.verifiedBy = verifiedBy;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public void setVerifiedAt(Instant verifiedAt) {
    this.verifiedAt = verifiedAt;
  }

  public boolean getAbnormalFlag() {
    return abnormalFlag;
  }

  public void setAbnormalFlag(boolean abnormalFlag) {
    this.abnormalFlag = abnormalFlag;
  }

  public boolean getCriticalFlag() {
    return criticalFlag;
  }

  public void setCriticalFlag(boolean criticalFlag) {
    this.criticalFlag = criticalFlag;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

}
