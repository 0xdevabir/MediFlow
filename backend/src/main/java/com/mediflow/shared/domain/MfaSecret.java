package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "mfa_secret")
public class MfaSecret extends BaseEntity {

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "secret_enc", nullable = false)
  private String secretEnc;

  @Column(name = "verified", nullable = false)
  private boolean verified;

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getSecretEnc() {
    return secretEnc;
  }

  public void setSecretEnc(String secretEnc) {
    this.secretEnc = secretEnc;
  }

  public boolean getVerified() {
    return verified;
  }

  public void setVerified(boolean verified) {
    this.verified = verified;
  }

}
