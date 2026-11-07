package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "queue_entry")
public class QueueEntry extends BaseEntity {

  @Column(name = "appointment_id", nullable = false, unique = true)
  private UUID appointmentId;

  @Column(name = "hospital_id", nullable = false)
  private UUID hospitalId;

  @Column(name = "token_no", nullable = false)
  private String tokenNo;

  @Column(name = "position", nullable = false)
  private int position;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority_level", nullable = false)
  private TriageLevel priorityLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private QueueStatus status;

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(UUID appointmentId) {
    this.appointmentId = appointmentId;
  }

  public UUID getHospitalId() {
    return hospitalId;
  }

  public void setHospitalId(UUID hospitalId) {
    this.hospitalId = hospitalId;
  }

  public String getTokenNo() {
    return tokenNo;
  }

  public void setTokenNo(String tokenNo) {
    this.tokenNo = tokenNo;
  }

  public int getPosition() {
    return position;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public TriageLevel getPriorityLevel() {
    return priorityLevel;
  }

  public void setPriorityLevel(TriageLevel priorityLevel) {
    this.priorityLevel = priorityLevel;
  }

  public QueueStatus getStatus() {
    return status;
  }

  public void setStatus(QueueStatus status) {
    this.status = status;
  }

}
