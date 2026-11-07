package com.mediflow.appointment.queue;

import com.mediflow.shared.domain.BaseEntity;
import com.mediflow.shared.domain.VitalClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** M2 queue ordering state beside the shared {@code queue_entry} row. */
@Entity
@Table(name = "appointment_queue_meta")
public class QueueMeta extends BaseEntity {

  @Column(name = "queue_entry_id", nullable = false, unique = true)
  private UUID queueEntryId;

  @Column(name = "appointment_id", nullable = false, unique = true)
  private UUID appointmentId;

  @Column(name = "doctor_id", nullable = false)
  private UUID doctorId;

  @Column(name = "queue_date", nullable = false)
  private LocalDate queueDate;

  @Column(name = "token_seq", nullable = false)
  private int tokenSeq;

  @Column(name = "arrived_at", nullable = false)
  private Instant arrivedAt;

  @Column(name = "slot_starts_at")
  private Instant slotStartsAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "vitals_level", length = 32)
  private VitalClass vitalsLevel;

  @Column(name = "override_bucket")
  private Integer overrideBucket;

  @Column(name = "override_sub")
  private Integer overrideSub;

  @Column(name = "override_time")
  private Instant overrideTime;

  @Column(name = "removed_reason", length = 64)
  private String removedReason;

  public UUID getQueueEntryId() {
    return queueEntryId;
  }

  public void setQueueEntryId(UUID queueEntryId) {
    this.queueEntryId = queueEntryId;
  }

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(UUID appointmentId) {
    this.appointmentId = appointmentId;
  }

  public UUID getDoctorId() {
    return doctorId;
  }

  public void setDoctorId(UUID doctorId) {
    this.doctorId = doctorId;
  }

  public LocalDate getQueueDate() {
    return queueDate;
  }

  public void setQueueDate(LocalDate queueDate) {
    this.queueDate = queueDate;
  }

  public int getTokenSeq() {
    return tokenSeq;
  }

  public void setTokenSeq(int tokenSeq) {
    this.tokenSeq = tokenSeq;
  }

  public Instant getArrivedAt() {
    return arrivedAt;
  }

  public void setArrivedAt(Instant arrivedAt) {
    this.arrivedAt = arrivedAt;
  }

  public Instant getSlotStartsAt() {
    return slotStartsAt;
  }

  public void setSlotStartsAt(Instant slotStartsAt) {
    this.slotStartsAt = slotStartsAt;
  }

  public VitalClass getVitalsLevel() {
    return vitalsLevel;
  }

  public void setVitalsLevel(VitalClass vitalsLevel) {
    this.vitalsLevel = vitalsLevel;
  }

  public Integer getOverrideBucket() {
    return overrideBucket;
  }

  public void setOverrideBucket(Integer overrideBucket) {
    this.overrideBucket = overrideBucket;
  }

  public Integer getOverrideSub() {
    return overrideSub;
  }

  public void setOverrideSub(Integer overrideSub) {
    this.overrideSub = overrideSub;
  }

  public Instant getOverrideTime() {
    return overrideTime;
  }

  public void setOverrideTime(Instant overrideTime) {
    this.overrideTime = overrideTime;
  }

  public String getRemovedReason() {
    return removedReason;
  }

  public void setRemovedReason(String removedReason) {
    this.removedReason = removedReason;
  }

  public QueueOrdering.SortKey overrideKey() {
    if (overrideBucket == null || overrideSub == null || overrideTime == null) {
      return null;
    }
    return new QueueOrdering.SortKey(overrideBucket, overrideSub, overrideTime);
  }

  public void applyOverride(QueueOrdering.SortKey key) {
    if (key == null) {
      overrideBucket = null;
      overrideSub = null;
      overrideTime = null;
      return;
    }
    overrideBucket = key.bucket();
    overrideSub = key.sub();
    overrideTime = key.time();
  }
}
