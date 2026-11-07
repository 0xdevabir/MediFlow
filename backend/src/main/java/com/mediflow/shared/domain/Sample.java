package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sample")
public class Sample extends BaseEntity {

  @Column(name = "lab_order_id", nullable = false)
  private UUID labOrderId;

  @Column(name = "barcode")
  private String barcode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private SampleStatus status;

  @Column(name = "collected_at")
  private Instant collectedAt;

  @Column(name = "received_at")
  private Instant receivedAt;

  public UUID getLabOrderId() {
    return labOrderId;
  }

  public void setLabOrderId(UUID labOrderId) {
    this.labOrderId = labOrderId;
  }

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  public SampleStatus getStatus() {
    return status;
  }

  public void setStatus(SampleStatus status) {
    this.status = status;
  }

  public Instant getCollectedAt() {
    return collectedAt;
  }

  public void setCollectedAt(Instant collectedAt) {
    this.collectedAt = collectedAt;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(Instant receivedAt) {
    this.receivedAt = receivedAt;
  }

}
