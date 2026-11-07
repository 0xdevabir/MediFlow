package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "ai_flag_override")
public class AiFlagOverride extends BaseEntity {

  @Column(name = "prescription_item_id", nullable = false)
  private UUID prescriptionItemId;

  @Column(name = "overridden_by", nullable = false)
  private UUID overriddenBy;

  @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
  private String reason;

  public UUID getPrescriptionItemId() {
    return prescriptionItemId;
  }

  public void setPrescriptionItemId(UUID prescriptionItemId) {
    this.prescriptionItemId = prescriptionItemId;
  }

  public UUID getOverriddenBy() {
    return overriddenBy;
  }

  public void setOverriddenBy(UUID overriddenBy) {
    this.overriddenBy = overriddenBy;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

}
