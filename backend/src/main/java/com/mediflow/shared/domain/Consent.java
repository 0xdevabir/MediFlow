package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent")
public class Consent extends BaseEntity {

  @Column(name = "patient_id")
  private UUID patientId;

  @Column(name = "user_id")
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "consent_type", nullable = false)
  private ConsentType consentType;

  @Column(name = "granted", nullable = false)
  private boolean granted;

  @Column(name = "version_label", nullable = false)
  private String versionLabel;

  @Column(name = "granted_at", nullable = false)
  private Instant grantedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public ConsentType getConsentType() {
    return consentType;
  }

  public void setConsentType(ConsentType consentType) {
    this.consentType = consentType;
  }

  public boolean getGranted() {
    return granted;
  }

  public void setGranted(boolean granted) {
    this.granted = granted;
  }

  public String getVersionLabel() {
    return versionLabel;
  }

  public void setVersionLabel(String versionLabel) {
    this.versionLabel = versionLabel;
  }

  public Instant getGrantedAt() {
    return grantedAt;
  }

  public void setGrantedAt(Instant grantedAt) {
    this.grantedAt = grantedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

}
