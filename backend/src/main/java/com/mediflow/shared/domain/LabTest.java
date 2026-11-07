package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "lab_test")
public class LabTest extends BaseEntity {

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "loinc_code")
  private String loincCode;

  @Column(name = "direct_allowed", nullable = false)
  private boolean directAllowed;

  @Column(name = "price")
  private BigDecimal price;

  @Column(name = "active", nullable = false)
  private boolean active;

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLoincCode() {
    return loincCode;
  }

  public void setLoincCode(String loincCode) {
    this.loincCode = loincCode;
  }

  public boolean getDirectAllowed() {
    return directAllowed;
  }

  public void setDirectAllowed(boolean directAllowed) {
    this.directAllowed = directAllowed;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

}
