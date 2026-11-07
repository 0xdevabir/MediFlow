package com.mediflow.appointment.queue;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only nurse reorder audit row (also mirrored to {@code AuditPort}). */
@Entity
@Table(name = "appointment_queue_override")
public class QueueOverrideLog extends BaseEntity {

  @Column(name = "queue_entry_id", nullable = false)
  private UUID queueEntryId;

  @Column(name = "appointment_id", nullable = false)
  private UUID appointmentId;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(name = "action", nullable = false, length = 32)
  private String action;

  @Column(name = "from_position")
  private Integer fromPosition;

  @Column(name = "to_position")
  private Integer toPosition;

  @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
  private String reason;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  public UUID getQueueEntryId() {
    return queueEntryId;
  }

  public void setQueueEntryId(UUID queueEntryId) {
    this.queueEntryId = queueEntryId;
  }

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(UUID appointmentId) {
    this.appointmentId = appointmentId;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public void setActorUserId(UUID actorUserId) {
    this.actorUserId = actorUserId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public Integer getFromPosition() {
    return fromPosition;
  }

  public void setFromPosition(Integer fromPosition) {
    this.fromPosition = fromPosition;
  }

  public Integer getToPosition() {
    return toPosition;
  }

  public void setToPosition(Integer toPosition) {
    this.toPosition = toPosition;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }
}
