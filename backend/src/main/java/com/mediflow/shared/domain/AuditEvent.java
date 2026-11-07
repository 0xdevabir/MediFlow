package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "patient_id")
  private UUID patientId;

  @Column(name = "action", nullable = false)
  private String action;

  @Column(name = "resource_type")
  private String resourceType;

  @Column(name = "resource_id")
  private UUID resourceId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "details", columnDefinition = "jsonb")
  private String details;

  @Column(name = "prev_hash")
  private String prevHash;

  @Column(name = "hash", nullable = false)
  private String hash;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (occurredAt == null) {
      occurredAt = createdAt;
    }
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public void setActorUserId(UUID actorUserId) {
    this.actorUserId = actorUserId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public void setResourceId(UUID resourceId) {
    this.resourceId = resourceId;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

  public String getPrevHash() {
    return prevHash;
  }

  public void setPrevHash(String prevHash) {
    this.prevHash = prevHash;
  }

  public String getHash() {
    return hash;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }

}
