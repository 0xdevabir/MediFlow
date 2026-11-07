package com.mediflow.identity.login;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Per-user login security state: failed attempts, lock-out, last TOTP step (anti-replay). */
@Entity
@Table(name = "identity_login_state")
public class LoginState {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts;

  @Column(name = "last_failed_at")
  private Instant lastFailedAt;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "mfa_last_used_step")
  private Long mfaLastUsedStep;

  @Column(name = "password_changed_at")
  private Instant passwordChangedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected LoginState() {}

  public LoginState(UUID userId) {
    this.userId = userId;
  }

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getUserId() {
    return userId;
  }

  public int getFailedAttempts() {
    return failedAttempts;
  }

  public void setFailedAttempts(int failedAttempts) {
    this.failedAttempts = failedAttempts;
  }

  public Instant getLastFailedAt() {
    return lastFailedAt;
  }

  public void setLastFailedAt(Instant lastFailedAt) {
    this.lastFailedAt = lastFailedAt;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public void setLockedUntil(Instant lockedUntil) {
    this.lockedUntil = lockedUntil;
  }

  public Long getMfaLastUsedStep() {
    return mfaLastUsedStep;
  }

  public void setMfaLastUsedStep(Long mfaLastUsedStep) {
    this.mfaLastUsedStep = mfaLastUsedStep;
  }

  public Instant getPasswordChangedAt() {
    return passwordChangedAt;
  }

  public void setPasswordChangedAt(Instant passwordChangedAt) {
    this.passwordChangedAt = passwordChangedAt;
  }
}
