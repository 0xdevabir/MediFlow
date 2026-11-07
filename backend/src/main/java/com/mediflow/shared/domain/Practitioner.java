package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "practitioner")
public class Practitioner extends BaseEntity {

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "role_label")
  private String roleLabel;

  @Column(name = "licence_no")
  private String licenceNo;

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getRoleLabel() {
    return roleLabel;
  }

  public void setRoleLabel(String roleLabel) {
    this.roleLabel = roleLabel;
  }

  public String getLicenceNo() {
    return licenceNo;
  }

  public void setLicenceNo(String licenceNo) {
    this.licenceNo = licenceNo;
  }

}
