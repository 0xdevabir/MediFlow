package com.mediflow.directory.event;

import com.mediflow.shared.event.DomainEvents;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Richer companion to {@link DomainEvents.PractitionerScheduleChanged} for notification /
 * appointment consumers that need slot-reconcile stats. Shared DomainEvents stays unchanged until
 * V2-01 promotes these fields (see {@code docs/plan/requests/V2-04.md}).
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
        practitionerRoleId, practitionerId, hospitalId, affectedBookedSlotIds, at);
  }

  public int affectedBookedCount() {
    return affectedBookedSlotIds.size();
  }
}
