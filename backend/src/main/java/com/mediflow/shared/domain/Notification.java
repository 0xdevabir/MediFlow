package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

  @Column(name = "recipient_user_id")
  private UUID recipientUserId;

  @Column(name = "patient_id")
  private UUID patientId;

  @Column(name = "channel", nullable = false)
  private String channel;

  @Column(name = "locale", nullable = false)
  private String locale;

  @Column(name = "subject")
  private String subject;

  @Column(name = "body", nullable = false, columnDefinition = "TEXT")
  private String body;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private NotificationStatus status;

  @Column(name = "sent_at")
  private Instant sentAt;

  public UUID getRecipientUserId() {
    return recipientUserId;
  }

  public void setRecipientUserId(UUID recipientUserId) {
    this.recipientUserId = recipientUserId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public void setStatus(NotificationStatus status) {
    this.status = status;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public void setSentAt(Instant sentAt) {
    this.sentAt = sentAt;
  }

}
