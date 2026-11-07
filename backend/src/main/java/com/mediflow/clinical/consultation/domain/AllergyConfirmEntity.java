package com.mediflow.clinical.consultation.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinical_allergy_confirm")
public class AllergyConfirmEntity extends BaseEntity {

  @Column(name = "allergy_id", nullable = false, unique = true)
  private UUID allergyId;

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "confirmed_by", nullable = false)
  private UUID confirmedBy;

  @Column(name = "confirmed_at", nullable = false)
  private Instant confirmedAt;

  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  public UUID getAllergyId() {
    return allergyId;
  }

  public void setAllergyId(UUID allergyId) {
    this.allergyId = allergyId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public UUID getConfirmedBy() {
    return confirmedBy;
  }

  public void setConfirmedBy(UUID confirmedBy) {
    this.confirmedBy = confirmedBy;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }

  public void setConfirmedAt(Instant confirmedAt) {
    this.confirmedAt = confirmedAt;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }
}
