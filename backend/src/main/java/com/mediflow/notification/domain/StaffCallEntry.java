package com.mediflow.notification.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Risk R7: a notification that could not be delivered electronically — staff must phone. */
@Entity
@Table(name = "staff_call_entry")
public class StaffCallEntry extends BaseEntity {

  @Column(name = "root_notification_id")
  private UUID rootNotificationId;

  @Column(name = "patient_id")
  private UUID patientId;

  @Column(name = "recipient_user_id")
  private UUID recipientUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false)
  private NotificationEventType eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority", nullable = false)
  private Priority priority = Priority.NORMAL;

  @Column(name = "reason", nullable = false)
  private String reason;

  @Column(name = "message", columnDefinition = "TEXT")
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private StaffCallStatus status = StaffCallStatus.OPEN;

  @Column(name = "resolved_by")
  private UUID resolvedBy;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  public UUID getRootNotificationId() {
    return rootNotificationId;
  }

  public void setRootNotificationId(UUID rootNotificationId) {
    this.rootNotificationId = rootNotificationId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public UUID getRecipientUserId() {
    return recipientUserId;
  }

  public void setRecipientUserId(UUID recipientUserId) {
    this.recipientUserId = recipientUserId;
  }

  public NotificationEventType getEventType() {
    return eventType;
  }

  public void setEventType(NotificationEventType eventType) {
    this.eventType = eventType;
  }

  public Priority getPriority() {
    return priority;
  }

  public void setPriority(Priority priority) {
    this.priority = priority;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500);
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public StaffCallStatus getStatus() {
    return status;
  }

  public void setStatus(StaffCallStatus status) {
    this.status = status;
  }

  public UUID getResolvedBy() {
    return resolvedBy;
  }

  public void setResolvedBy(UUID resolvedBy) {
    this.resolvedBy = resolvedBy;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }
}
