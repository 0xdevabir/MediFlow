package com.mediflow.directory.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published (in the admin's transaction) whenever an administrator changes a doctor's weekly
 * schedule or (de)activates a practitioner role. Listen with {@code @TransactionalEventListener}.
 *
 * <p>{@code affectedBookedSlotIds} are future slots that already have bookings but no longer
 * match the new schedule. They are kept (bookings are never cancelled automatically); T10 should
 * notify the patients booked on them and T04/front desk should reschedule (research risk table:
 * "schedule change alerts booked patients; front desk confirms by phone").
 *
 * <p>Requested to move into {@code shared.event.DomainEvents} (see docs/plan/requests/T03.md).
 */
public record PractitionerScheduleChanged(
    UUID practitionerRoleId,
    UUID practitionerId,
    UUID hospitalId,
    List<UUID> affectedBookedSlotIds,
    Instant at) {

  public PractitionerScheduleChanged {
    affectedBookedSlotIds = List.copyOf(affectedBookedSlotIds);
  }
}
