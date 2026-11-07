package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment")
public class Appointment extends BaseEntity {

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "doctor_id")
  private UUID doctorId;

  @Column(name = "hospital_id", nullable = false)
  private UUID hospitalId;

  @Column(name = "slot_id")
  private UUID slotId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private AppointmentStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority_level", nullable = false)
  private TriageLevel priorityLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "emergency_level")
  private TriageLevel emergencyLevel;

  @Column(name = "booked_at", nullable = false)
  private Instant bookedAt;

  @Column(name = "checked_in_at")
  private Instant checkedInAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public UUID getDoctorId() {
    return doctorId;
  }

  public void setDoctorId(UUID doctorId) {
    this.doctorId = doctorId;
  }

  public UUID getHospitalId() {
    return hospitalId;
  }

  public void setHospitalId(UUID hospitalId) {
    this.hospitalId = hospitalId;
  }

  public UUID getSlotId() {
    return slotId;
  }

  public void setSlotId(UUID slotId) {
    this.slotId = slotId;
  }

  public AppointmentStatus getStatus() {
    return status;
  }

  public void setStatus(AppointmentStatus status) {
    this.status = status;
  }

  public TriageLevel getPriorityLevel() {
    return priorityLevel;
  }

  public void setPriorityLevel(TriageLevel priorityLevel) {
    this.priorityLevel = priorityLevel;
  }

  public TriageLevel getEmergencyLevel() {
    return emergencyLevel;
  }

  public void setEmergencyLevel(TriageLevel emergencyLevel) {
    this.emergencyLevel = emergencyLevel;
  }

  public Instant getBookedAt() {
    return bookedAt;
  }

  public void setBookedAt(Instant bookedAt) {
    this.bookedAt = bookedAt;
  }

  public Instant getCheckedInAt() {
    return checkedInAt;
  }

  public void setCheckedInAt(Instant checkedInAt) {
    this.checkedInAt = checkedInAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public void setCancelledAt(Instant cancelledAt) {
    this.cancelledAt = cancelledAt;
  }

}
