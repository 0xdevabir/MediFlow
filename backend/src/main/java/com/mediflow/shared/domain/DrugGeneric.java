package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "drug_generic")
public class DrugGeneric extends BaseEntity {

  @Column(name = "name", nullable = false, unique = true)
  private String name;

  @Column(name = "atc_code")
  private String atcCode;

  @Column(name = "rxnorm_code")
  private String rxnormCode;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAtcCode() {
    return atcCode;
  }

  public void setAtcCode(String atcCode) {
    this.atcCode = atcCode;
  }

  public String getRxnormCode() {
    return rxnormCode;
  }

  public void setRxnormCode(String rxnormCode) {
    this.rxnormCode = rxnormCode;
  }

}
