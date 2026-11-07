package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "drug_interaction")
public class DrugInteraction extends BaseEntity {

  @Column(name = "generic_a_id", nullable = false)
  private UUID genericAId;

  @Column(name = "generic_b_id", nullable = false)
  private UUID genericBId;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", nullable = false)
  private ConflictSeverity severity;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  public UUID getGenericAId() {
    return genericAId;
  }

  public void setGenericAId(UUID genericAId) {
    this.genericAId = genericAId;
  }

  public UUID getGenericBId() {
    return genericBId;
  }

  public void setGenericBId(UUID genericBId) {
    this.genericBId = genericBId;
  }

  public ConflictSeverity getSeverity() {
    return severity;
  }

  public void setSeverity(ConflictSeverity severity) {
    this.severity = severity;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

}
