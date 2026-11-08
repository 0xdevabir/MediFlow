package com.mediflow.directory.event;

import com.mediflow.shared.event.DomainEvents;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Local companion for schedule edits. Prefer publishing {@link
 * DomainEvents.PractitionerScheduleChanged} (now carries the same counters). Kept for tests /
 * transitional listeners.
 */
public record DirectoryScheduleChangeDetail(
    UUID practitionerRoleId,
    UUID practitionerId,
    UUID hospitalId,
    List<UUID> affectedBookedSlotIds,
    int slotsCreated,
    int slotsUpdated,
    int slotsRemoved,
    int slotsClosed,
    boolean roleBecameInactive,
    Instant at) {

  public DirectoryScheduleChangeDetail {
    affectedBookedSlotIds =
        affectedBookedSlotIds == null ? List.of() : List.copyOf(affectedBookedSlotIds);
  }

  public DomainEvents.PractitionerScheduleChanged toShared() {
    return new DomainEvents.PractitionerScheduleChanged(
        practitionerRoleId,
        practitionerId,
        hospitalId,
        affectedBookedSlotIds,
        slotsCreated,
        slotsUpdated,
        slotsRemoved,
        slotsClosed,
        roleBecameInactive,
        at);
  }

  public int affectedBookedCount() {
    return affectedBookedSlotIds.size();
  }
}
