package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "triage")
public class Triage extends BaseEntity {

  @Column(name = "appointment_id", nullable = false, unique = true)
  private UUID appointmentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "level", nullable = false)
  private TriageLevel level;

  @Column(name = "set_by")
  private UUID setBy;

  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  @Column(name = "set_at", nullable = false)
  private Instant setAt;

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(UUID appointmentId) {
    this.appointmentId = appointmentId;
  }

  public TriageLevel getLevel() {
    return level;
  }

  public void setLevel(TriageLevel level) {
    this.level = level;
  }

  public UUID getSetBy() {
    return setBy;
  }

  public void setSetBy(UUID setBy) {
    this.setBy = setBy;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public Instant getSetAt() {
    return setAt;
  }

  public void setSetAt(Instant setAt) {
    this.setAt = setAt;
  }

}
