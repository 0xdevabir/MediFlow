package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "prescription")
public class Prescription extends BaseEntity {

  @Column(name = "encounter_id", nullable = false)
  private UUID encounterId;

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private PrescriptionStatus status;

  @Column(name = "signed_at")
  private Instant signedAt;

  @Column(name = "signed_by")
  private UUID signedBy;

  @Column(name = "revoked_at")
  private Instant revokedAt;

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

  public PrescriptionStatus getStatus() {
    return status;
  }

  public void setStatus(PrescriptionStatus status) {
    this.status = status;
  }

  public Instant getSignedAt() {
    return signedAt;
  }

  public void setSignedAt(Instant signedAt) {
    this.signedAt = signedAt;
  }

  public UUID getSignedBy() {
    return signedBy;
  }

  public void setSignedBy(UUID signedBy) {
    this.signedBy = signedBy;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

}
