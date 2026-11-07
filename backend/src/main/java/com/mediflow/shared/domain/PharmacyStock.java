package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "pharmacy_stock")
public class PharmacyStock extends BaseEntity {

  @Column(name = "hospital_id", nullable = false)
  private UUID hospitalId;

  @Column(name = "brand_id", nullable = false)
  private UUID brandId;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Column(name = "reorder_level", nullable = false)
  private int reorderLevel;

  public UUID getHospitalId() {
    return hospitalId;
  }

  public void setHospitalId(UUID hospitalId) {
    this.hospitalId = hospitalId;
  }

  public UUID getBrandId() {
    return brandId;
  }

  public void setBrandId(UUID brandId) {
    this.brandId = brandId;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }

  public int getReorderLevel() {
    return reorderLevel;
  }

  public void setReorderLevel(int reorderLevel) {
    this.reorderLevel = reorderLevel;
  }

}
