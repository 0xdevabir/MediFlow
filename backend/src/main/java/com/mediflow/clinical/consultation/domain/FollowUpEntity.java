package com.mediflow.clinical.consultation.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "clinical_follow_up")
public class FollowUpEntity extends BaseEntity {

  @Column(name = "encounter_id", nullable = false, unique = true)
  private UUID encounterId;

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "follow_up_date", nullable = false)
  private LocalDate followUpDate;

  @Column(name = "note_id")
  private UUID noteId;

  @Column(name = "scheduled_by", nullable = false)
  private UUID scheduledBy;

  public UUID getEncounterId() {
    return encounterId;
  }

  public void setEncounterId(UUID encounterId) {
    this.encounterId = encounterId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public LocalDate getFollowUpDate() {
    return followUpDate;
  }

  public void setFollowUpDate(LocalDate followUpDate) {
    this.followUpDate = followUpDate;
  }

  public UUID getNoteId() {
    return noteId;
  }

  public void setNoteId(UUID noteId) {
    this.noteId = noteId;
  }

  public UUID getScheduledBy() {
    return scheduledBy;
  }

  public void setScheduledBy(UUID scheduledBy) {
    this.scheduledBy = scheduledBy;
  }
}
