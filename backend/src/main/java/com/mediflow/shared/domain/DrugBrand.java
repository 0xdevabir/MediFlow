package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "drug_brand")
public class DrugBrand extends BaseEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "dgda_reg_no")
  private String dgdaRegNo;

  @Column(name = "strength")
  private String strength;

  @Column(name = "form")
  private String form;

  @Column(name = "generic_id", nullable = false)
  private UUID genericId;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDgdaRegNo() {
    return dgdaRegNo;
  }

  public void setDgdaRegNo(String dgdaRegNo) {
    this.dgdaRegNo = dgdaRegNo;
  }

  public String getStrength() {
    return strength;
  }

  public void setStrength(String strength) {
    this.strength = strength;
  }

  public String getForm() {
    return form;
  }

  public void setForm(String form) {
    this.form = form;
  }

  public UUID getGenericId() {
    return genericId;
  }

  public void setGenericId(UUID genericId) {
    this.genericId = genericId;
  }

}
