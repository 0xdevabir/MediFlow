package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "red_alert")
public class RedAlert extends BaseEntity {

  @Column(name = "encounter_id")
  private UUID encounterId;

  @Column(name = "appointment_id")
  private UUID appointmentId;

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "hospital_id", nullable = false)
  private UUID hospitalId;

  @Enumerated(EnumType.STRING)
  @Column(name = "level", nullable = false)
  private TriageLevel level;

  @Column(name = "raised_by")
  private UUID raisedBy;

  @Column(name = "reason", columnDefinition = "TEXT")
  private String reason;

  @Column(name = "raised_at", nullable = false)
  private Instant raisedAt;

  @Column(name = "acknowledged_at")
  private Instant acknowledgedAt;

  @Column(name = "handover_status")
  private String handoverStatus;

  public UUID getEncounterId() {
    return encounterId;
  }

  public void setEncounterId(UUID encounterId) {
    this.encounterId = encounterId;
  }

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(UUID appointmentId) {
    this.appointmentId = appointmentId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public UUID getHospitalId() {
    return hospitalId;
  }

  public void setHospitalId(UUID hospitalId) {
    this.hospitalId = hospitalId;
  }

  public TriageLevel getLevel() {
    return level;
  }

  public void setLevel(TriageLevel level) {
    this.level = level;
  }

  public UUID getRaisedBy() {
    return raisedBy;
  }

  public void setRaisedBy(UUID raisedBy) {
    this.raisedBy = raisedBy;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Instant getRaisedAt() {
    return raisedAt;
  }

  public void setRaisedAt(Instant raisedAt) {
    this.raisedAt = raisedAt;
  }

  public Instant getAcknowledgedAt() {
    return acknowledgedAt;
  }

  public void setAcknowledgedAt(Instant acknowledgedAt) {
    this.acknowledgedAt = acknowledgedAt;
  }

  public String getHandoverStatus() {
    return handoverStatus;
  }

  public void setHandoverStatus(String handoverStatus) {
    this.handoverStatus = handoverStatus;
  }

}
