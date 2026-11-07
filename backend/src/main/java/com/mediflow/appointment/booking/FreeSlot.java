package com.mediflow.appointment.booking;

import java.time.Instant;
import java.util.UUID;

/** A bookable slot with the directory labels needed to show it. */
public record FreeSlot(
    UUID slotId,
    Instant startsAt,
    Instant endsAt,
    int capacity,
    int bookedCount,
    UUID practitionerRoleId,
    UUID doctorId,
    String doctorName,
    UUID hospitalId,
    String hospitalName,
    UUID departmentId,
    String departmentName) {

  public int remaining() {
    return Math.max(0, capacity - bookedCount);
  }
}
