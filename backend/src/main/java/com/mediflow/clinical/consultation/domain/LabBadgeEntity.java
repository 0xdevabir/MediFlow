package com.mediflow.clinical.consultation.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinical_lab_badge")
public class LabBadgeEntity extends BaseEntity {

  @Column(name = "doctor_user_id", nullable = false)
  private UUID doctorUserId;

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "report_id", nullable = false)
  private UUID reportId;

  @Column(name = "lab_order_id", nullable = false)
  private UUID labOrderId;

  @Column(name = "abnormal", nullable = false)
  private boolean abnormal;

  @Column(name = "critical", nullable = false)
  private boolean critical;

  @Column(name = "read_at")
  private Instant readAt;

  public UUID getDoctorUserId() {
    return doctorUserId;
  }

  public void setDoctorUserId(UUID doctorUserId) {
    this.doctorUserId = doctorUserId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public UUID getReportId() {
    return reportId;
  }

  public void setReportId(UUID reportId) {
    this.reportId = reportId;
  }

  public UUID getLabOrderId() {
    return labOrderId;
  }

  public void setLabOrderId(UUID labOrderId) {
    this.labOrderId = labOrderId;
  }

  public boolean isAbnormal() {
    return abnormal;
  }

  public void setAbnormal(boolean abnormal) {
    this.abnormal = abnormal;
  }

  public boolean isCritical() {
    return critical;
  }

  public void setCritical(boolean critical) {
    this.critical = critical;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public void setReadAt(Instant readAt) {
    this.readAt = readAt;
  }
}
