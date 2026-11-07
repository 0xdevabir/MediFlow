package com.mediflow.notification.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Delivery metadata for one {@code notification} row (retries, escalation chain, read state). */
@Entity
@Table(name = "notification_delivery")
public class NotificationDelivery extends BaseEntity {

  @Column(name = "notification_id", nullable = false, unique = true)
  private UUID notificationId;

  @Column(name = "root_notification_id", nullable = false)
  private UUID rootNotificationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false)
  private NotificationEventType eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority", nullable = false)
  private Priority priority = Priority.NORMAL;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "escalated_to_id")
  private UUID escalatedToId;

  @Column(name = "read_at")
  private Instant readAt;

  public UUID getNotificationId() {
    return notificationId;
  }

  public void setNotificationId(UUID notificationId) {
    this.notificationId = notificationId;
  }

  public UUID getRootNotificationId() {
    return rootNotificationId;
  }

  public void setRootNotificationId(UUID rootNotificationId) {
    this.rootNotificationId = rootNotificationId;
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

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int attempts) {
    this.attempts = attempts;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError =
        lastError == null || lastError.length() <= 500 ? lastError : lastError.substring(0, 500);
  }

  public UUID getEscalatedToId() {
    return escalatedToId;
  }

  public void setEscalatedToId(UUID escalatedToId) {
    this.escalatedToId = escalatedToId;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public void setReadAt(Instant readAt) {
    this.readAt = readAt;
  }
}
