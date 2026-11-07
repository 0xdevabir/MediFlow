package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "prescription_item")
public class PrescriptionItem extends BaseEntity {

  @Column(name = "prescription_id", nullable = false)
  private UUID prescriptionId;

  @Column(name = "brand_id")
  private UUID brandId;

  @Column(name = "generic_id")
  private UUID genericId;

  @Column(name = "dose")
  private String dose;

  @Column(name = "route")
  private String route;

  @Column(name = "frequency")
  private String frequency;

  @Column(name = "duration")
  private String duration;

  @Column(name = "confidence")
  private BigDecimal confidence;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_status", nullable = false)
  private ReviewStatus reviewStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "conflict_severity")
  private ConflictSeverity conflictSeverity;

  @Column(name = "raw_text", columnDefinition = "TEXT")
  private String rawText;

  public UUID getPrescriptionId() {
    return prescriptionId;
  }

  public void setPrescriptionId(UUID prescriptionId) {
    this.prescriptionId = prescriptionId;
  }

  public UUID getBrandId() {
    return brandId;
  }

  public void setBrandId(UUID brandId) {
    this.brandId = brandId;
  }

  public UUID getGenericId() {
    return genericId;
  }

  public void setGenericId(UUID genericId) {
    this.genericId = genericId;
  }

  public String getDose() {
    return dose;
  }

  public void setDose(String dose) {
    this.dose = dose;
  }

  public String getRoute() {
    return route;
  }

  public void setRoute(String route) {
    this.route = route;
  }

  public String getFrequency() {
    return frequency;
  }

  public void setFrequency(String frequency) {
    this.frequency = frequency;
  }

  public String getDuration() {
    return duration;
  }

  public void setDuration(String duration) {
    this.duration = duration;
  }

  public BigDecimal getConfidence() {
    return confidence;
  }

  public void setConfidence(BigDecimal confidence) {
    this.confidence = confidence;
  }

  public ReviewStatus getReviewStatus() {
    return reviewStatus;
  }

  public void setReviewStatus(ReviewStatus reviewStatus) {
    this.reviewStatus = reviewStatus;
  }

  public ConflictSeverity getConflictSeverity() {
    return conflictSeverity;
  }

  public void setConflictSeverity(ConflictSeverity conflictSeverity) {
    this.conflictSeverity = conflictSeverity;
  }

  public String getRawText() {
    return rawText;
  }

  public void setRawText(String rawText) {
    this.rawText = rawText;
  }

}
