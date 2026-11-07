package com.mediflow.notification.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "adherence_log")
public class AdherenceLog extends BaseEntity {

  @Column(name = "reminder_id", nullable = false, unique = true)
  private UUID reminderId;

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false)
  private AdherenceOutcome outcome;

  @Column(name = "acknowledged_at", nullable = false)
  private Instant acknowledgedAt;

  protected AdherenceLog() {}

  public AdherenceLog(UUID reminderId, UUID patientId, AdherenceOutcome outcome, Instant at) {
    this.reminderId = reminderId;
    this.patientId = patientId;
    this.outcome = outcome;
    this.acknowledgedAt = at;
  }

  public UUID getReminderId() {
    return reminderId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public AdherenceOutcome getOutcome() {
    return outcome;
  }

  public void setOutcome(AdherenceOutcome outcome) {
    this.outcome = outcome;
  }

  public Instant getAcknowledgedAt() {
    return acknowledgedAt;
  }

  public void setAcknowledgedAt(Instant acknowledgedAt) {
    this.acknowledgedAt = acknowledgedAt;
  }
}
