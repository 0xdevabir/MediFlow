package com.mediflow.appointment.booking;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** M2-only booking state next to the shared {@code appointment} row. */
@Entity
@Table(name = "appointment_booking_meta")
public class BookingMeta extends BaseEntity {

  @Column(name = "appointment_id", nullable = false, unique = true)
  private UUID appointmentId;

  @Column(name = "booked_by")
  private UUID bookedBy;

  @Column(name = "checkin_code", nullable = false, unique = true)
  private String checkinCode;

  @Column(name = "emergency_flag", nullable = false)
  private boolean emergencyFlag;

  @Column(name = "emergency_flagged_by")
  private UUID emergencyFlaggedBy;

  @Column(name = "emergency_flagged_at")
  private Instant emergencyFlaggedAt;

  @Column(name = "emergency_note", columnDefinition = "TEXT")
  private String emergencyNote;

  @Column(name = "rescheduled_from")
  private UUID rescheduledFrom;

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(UUID appointmentId) {
    this.appointmentId = appointmentId;
  }

  public UUID getBookedBy() {
    return bookedBy;
  }

  public void setBookedBy(UUID bookedBy) {
    this.bookedBy = bookedBy;
  }

  public String getCheckinCode() {
    return checkinCode;
  }

  public void setCheckinCode(String checkinCode) {
    this.checkinCode = checkinCode;
  }

  public boolean isEmergencyFlag() {
    return emergencyFlag;
  }

  public void setEmergencyFlag(boolean emergencyFlag) {
    this.emergencyFlag = emergencyFlag;
  }

  public UUID getEmergencyFlaggedBy() {
    return emergencyFlaggedBy;
  }

  public void setEmergencyFlaggedBy(UUID emergencyFlaggedBy) {
    this.emergencyFlaggedBy = emergencyFlaggedBy;
  }

  public Instant getEmergencyFlaggedAt() {
    return emergencyFlaggedAt;
  }

  public void setEmergencyFlaggedAt(Instant emergencyFlaggedAt) {
    this.emergencyFlaggedAt = emergencyFlaggedAt;
  }

  public String getEmergencyNote() {
    return emergencyNote;
  }

  public void setEmergencyNote(String emergencyNote) {
    this.emergencyNote = emergencyNote;
  }

  public UUID getRescheduledFrom() {
    return rescheduledFrom;
  }

  public void setRescheduledFrom(UUID rescheduledFrom) {
    this.rescheduledFrom = rescheduledFrom;
  }
}
