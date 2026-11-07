package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reminder")
public class Reminder extends BaseEntity {

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "reminder_type", nullable = false)
  private String reminderType;

  @Column(name = "channel", nullable = false)
  private String channel;

  @Column(name = "scheduled_at", nullable = false)
  private Instant scheduledAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ReminderStatus status;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public String getReminderType() {
    return reminderType;
  }

  public void setReminderType(String reminderType) {
    this.reminderType = reminderType;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public Instant getScheduledAt() {
    return scheduledAt;
  }

  public void setScheduledAt(Instant scheduledAt) {
    this.scheduledAt = scheduledAt;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public ReminderStatus getStatus() {
    return status;
  }

  public void setStatus(ReminderStatus status) {
    this.status = status;
  }

}
