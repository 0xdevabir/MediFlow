package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lab_order")
public class LabOrder extends BaseEntity {

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "hospital_id", nullable = false)
  private UUID hospitalId;

  @Column(name = "encounter_id")
  private UUID encounterId;

  @Column(name = "test_id")
  private UUID testId;

  @Column(name = "package_id")
  private UUID packageId;

  @Enumerated(EnumType.STRING)
  @Column(name = "order_type", nullable = false)
  private OrderType orderType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private LabOrderStatus status;

  @Column(name = "ordered_by")
  private UUID orderedBy;

  @Column(name = "ordered_at", nullable = false)
  private Instant orderedAt;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public UUID getHospitalId() {
    return hospitalId;
  }

  public void setHospitalId(UUID hospitalId) {
    this.hospitalId = hospitalId;
  }

  public UUID getEncounterId() {
    return encounterId;
  }

  public void setEncounterId(UUID encounterId) {
    this.encounterId = encounterId;
  }

  public UUID getTestId() {
    return testId;
  }

  public void setTestId(UUID testId) {
    this.testId = testId;
  }

  public UUID getPackageId() {
    return packageId;
  }

  public void setPackageId(UUID packageId) {
    this.packageId = packageId;
  }

  public OrderType getOrderType() {
    return orderType;
  }

  public void setOrderType(OrderType orderType) {
    this.orderType = orderType;
  }

  public LabOrderStatus getStatus() {
    return status;
  }

  public void setStatus(LabOrderStatus status) {
    this.status = status;
  }

  public UUID getOrderedBy() {
    return orderedBy;
  }

  public void setOrderedBy(UUID orderedBy) {
    this.orderedBy = orderedBy;
  }

  public Instant getOrderedAt() {
    return orderedAt;
  }

  public void setOrderedAt(Instant orderedAt) {
    this.orderedAt = orderedAt;
  }

}
