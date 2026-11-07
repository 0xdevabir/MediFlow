package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "practitioner_role")
public class PractitionerRoleEntity extends BaseEntity {

  @Column(name = "practitioner_id", nullable = false)
  private UUID practitionerId;

  @Column(name = "hospital_id", nullable = false)
  private UUID hospitalId;

  @Column(name = "department_id", nullable = false)
  private UUID departmentId;

  @Column(name = "schedule", columnDefinition = "TEXT")
  private String schedule;

  @Column(name = "active", nullable = false)
  private boolean active;

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public void setPractitionerId(UUID practitionerId) {
    this.practitionerId = practitionerId;
  }

  public UUID getHospitalId() {
    return hospitalId;
  }

  public void setHospitalId(UUID hospitalId) {
    this.hospitalId = hospitalId;
  }

  public UUID getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(UUID departmentId) {
    this.departmentId = departmentId;
  }

  public String getSchedule() {
    return schedule;
  }

  public void setSchedule(String schedule) {
    this.schedule = schedule;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

}
