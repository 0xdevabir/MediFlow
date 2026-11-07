package com.mediflow.identity.breakglass;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Admin's after-the-fact review of one break-glass access. */
@Entity
@Table(name = "identity_break_glass_review")
public class BreakGlassReview extends BaseEntity {

  public enum Outcome {
    JUSTIFIED,
    NOT_JUSTIFIED
  }

  @Column(name = "break_glass_id", nullable = false, unique = true)
  private UUID breakGlassId;

  @Column(name = "reviewed_by", nullable = false)
  private UUID reviewedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false, length = 32)
  private Outcome outcome;

  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  @Column(name = "reviewed_at", nullable = false)
  private Instant reviewedAt;

  public UUID getBreakGlassId() {
    return breakGlassId;
  }

  public void setBreakGlassId(UUID breakGlassId) {
    this.breakGlassId = breakGlassId;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public void setReviewedBy(UUID reviewedBy) {
    this.reviewedBy = reviewedBy;
  }

  public Outcome getOutcome() {
    return outcome;
  }

  public void setOutcome(Outcome outcome) {
    this.outcome = outcome;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public void setReviewedAt(Instant reviewedAt) {
    this.reviewedAt = reviewedAt;
  }
}
