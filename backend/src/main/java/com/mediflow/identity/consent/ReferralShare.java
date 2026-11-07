package com.mediflow.identity.consent;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Time-limited "share my record with another doctor", backed by a consent row. */
@Entity
@Table(name = "identity_referral_share")
public class ReferralShare extends BaseEntity {

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "grantee_user_id", nullable = false)
  private UUID granteeUserId;

  @Column(name = "granted_by", nullable = false)
  private UUID grantedBy;

  @Column(name = "consent_id", nullable = false)
  private UUID consentId;

  @Column(name = "reason", columnDefinition = "TEXT")
  private String reason;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  public boolean activeAt(Instant now) {
    return revokedAt == null && !startsAt.isAfter(now) && now.isBefore(expiresAt);
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public UUID getGranteeUserId() {
    return granteeUserId;
  }

  public void setGranteeUserId(UUID granteeUserId) {
    this.granteeUserId = granteeUserId;
  }

  public UUID getGrantedBy() {
    return grantedBy;
  }

  public void setGrantedBy(UUID grantedBy) {
    this.grantedBy = grantedBy;
  }

  public UUID getConsentId() {
    return consentId;
  }

  public void setConsentId(UUID consentId) {
    this.consentId = consentId;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(Instant startsAt) {
    this.startsAt = startsAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }
}
