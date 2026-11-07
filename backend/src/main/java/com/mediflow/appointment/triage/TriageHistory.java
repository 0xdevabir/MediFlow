package com.mediflow.appointment.triage;

import com.mediflow.shared.domain.BaseEntity;
import com.mediflow.shared.domain.TriageLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only triage decision history. */
@Entity
@Table(name = "appointment_triage_history")
public class TriageHistory extends BaseEntity {

  @Column(name = "appointment_id", nullable = false)
  private UUID appointmentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "level", nullable = false, length = 32)
  private TriageLevel level;

  @Column(name = "symptom_severity", length = 32)
  private String symptomSeverity;

  @Column(name = "source", nullable = false, length = 32)
  private String source;

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

  public String getSymptomSeverity() {
    return symptomSeverity;
  }

  public void setSymptomSeverity(String symptomSeverity) {
    this.symptomSeverity = symptomSeverity;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
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
