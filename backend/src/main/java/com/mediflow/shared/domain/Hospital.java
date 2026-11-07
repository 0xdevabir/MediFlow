package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "hospital")
public class Hospital extends BaseEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "type")
  private String type;

  @Column(name = "address", columnDefinition = "TEXT")
  private String address;

  @Column(name = "phone")
  private String phone;

  @Column(name = "emergency_phone")
  private String emergencyPhone;

  @Column(name = "opening_hours", columnDefinition = "TEXT")
  private String openingHours;

  @Column(name = "active", nullable = false)
  private boolean active;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getEmergencyPhone() {
    return emergencyPhone;
  }

  public void setEmergencyPhone(String emergencyPhone) {
    this.emergencyPhone = emergencyPhone;
  }

  public String getOpeningHours() {
    return openingHours;
  }

  public void setOpeningHours(String openingHours) {
    this.openingHours = openingHours;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

}
