package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "setting_change_request")
public class SettingChangeRequest extends BaseEntity {

  @Column(name = "setting_key", nullable = false)
  private String settingKey;

  @Column(name = "proposed_value", nullable = false, columnDefinition = "TEXT")
  private String proposedValue;

  @Column(name = "requested_by", nullable = false)
  private UUID requestedBy;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private SettingChangeStatus status;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  public String getSettingKey() {
    return settingKey;
  }

  public void setSettingKey(String settingKey) {
    this.settingKey = settingKey;
  }

  public String getProposedValue() {
    return proposedValue;
  }

  public void setProposedValue(String proposedValue) {
    this.proposedValue = proposedValue;
  }

  public UUID getRequestedBy() {
    return requestedBy;
  }

  public void setRequestedBy(UUID requestedBy) {
    this.requestedBy = requestedBy;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public void setApprovedBy(UUID approvedBy) {
    this.approvedBy = approvedBy;
  }

  public SettingChangeStatus getStatus() {
    return status;
  }

  public void setStatus(SettingChangeStatus status) {
    this.status = status;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public void setRequestedAt(Instant requestedAt) {
    this.requestedAt = requestedAt;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(Instant decidedAt) {
    this.decidedAt = decidedAt;
  }

}
