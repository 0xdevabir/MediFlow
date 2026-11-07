package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "allergy")
public class Allergy extends BaseEntity {

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "substance", nullable = false)
  private String substance;

  @Column(name = "substance_code")
  private String substanceCode;

  @Column(name = "criticality")
  private String criticality;

  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public String getSubstance() {
    return substance;
  }

  public void setSubstance(String substance) {
    this.substance = substance;
  }

  public String getSubstanceCode() {
    return substanceCode;
  }

  public void setSubstanceCode(String substanceCode) {
    this.substanceCode = substanceCode;
  }

  public String getCriticality() {
    return criticality;
  }

  public void setCriticality(String criticality) {
    this.criticality = criticality;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

}
