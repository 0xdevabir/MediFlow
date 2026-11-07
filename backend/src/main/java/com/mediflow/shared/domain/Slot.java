package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slot")
public class Slot extends BaseEntity {

  @Column(name = "practitioner_role_id", nullable = false)
  private UUID practitionerRoleId;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "capacity", nullable = false)
  private int capacity;

  @Column(name = "booked_count", nullable = false)
  private int bookedCount;

  public UUID getPractitionerRoleId() {
    return practitionerRoleId;
  }

  public void setPractitionerRoleId(UUID practitionerRoleId) {
    this.practitionerRoleId = practitionerRoleId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(Instant startsAt) {
    this.startsAt = startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(Instant endsAt) {
    this.endsAt = endsAt;
  }

  public int getCapacity() {
    return capacity;
  }

  public void setCapacity(int capacity) {
    this.capacity = capacity;
  }

  public int getBookedCount() {
    return bookedCount;
  }

  public void setBookedCount(int bookedCount) {
    this.bookedCount = bookedCount;
  }

}
