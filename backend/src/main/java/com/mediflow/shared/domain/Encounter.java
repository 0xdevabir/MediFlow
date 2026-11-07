package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "encounter")
public class Encounter extends BaseEntity {

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "doctor_id")
  private UUID doctorId;

  @Column(name = "hospital_id", nullable = false)
  private UUID hospitalId;

  @Column(name = "appointment_id", unique = true)
  private UUID appointmentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private EncounterStatus status;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "diagnosis_summary", columnDefinition = "TEXT")
  private String diagnosisSummary;

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

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(UUID appointmentId) {
    this.appointmentId = appointmentId;
  }

  public EncounterStatus getStatus() {
    return status;
  }

  public void setStatus(EncounterStatus status) {
    this.status = status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public void setEndedAt(Instant endedAt) {
    this.endedAt = endedAt;
  }

  public String getDiagnosisSummary() {
    return diagnosisSummary;
  }

  public void setDiagnosisSummary(String diagnosisSummary) {
    this.diagnosisSummary = diagnosisSummary;
  }

}
