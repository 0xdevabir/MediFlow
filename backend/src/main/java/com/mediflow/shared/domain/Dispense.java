package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispense")
public class Dispense extends BaseEntity {

  @Column(name = "prescription_id", nullable = false)
  private UUID prescriptionId;

  @Column(name = "prescription_item_id")
  private UUID prescriptionItemId;

  @Column(name = "pharmacist_id")
  private UUID pharmacistId;

  @Column(name = "hospital_id")
  private UUID hospitalId;

  @Column(name = "quantity")
  private Integer quantity;

  @Column(name = "qr_token")
  private String qrToken;

  @Column(name = "dispensed_at", nullable = false)
  private Instant dispensedAt;

  public UUID getPrescriptionId() {
    return prescriptionId;
  }

  public void setPrescriptionId(UUID prescriptionId) {
    this.prescriptionId = prescriptionId;
  }

  public UUID getPrescriptionItemId() {
    return prescriptionItemId;
  }

  public void setPrescriptionItemId(UUID prescriptionItemId) {
    this.prescriptionItemId = prescriptionItemId;
  }

  public UUID getPharmacistId() {
    return pharmacistId;
  }

  public void setPharmacistId(UUID pharmacistId) {
    this.pharmacistId = pharmacistId;
  }

  public UUID getHospitalId() {
    return hospitalId;
  }

  public void setHospitalId(UUID hospitalId) {
    this.hospitalId = hospitalId;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }

  public String getQrToken() {
    return qrToken;
  }

  public void setQrToken(String qrToken) {
    this.qrToken = qrToken;
  }

  public Instant getDispensedAt() {
    return dispensedAt;
  }

  public void setDispensedAt(Instant dispensedAt) {
    this.dispensedAt = dispensedAt;
  }

}
