package com.mediflow.directory.event;

import com.mediflow.shared.event.DomainEvents;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @deprecated Prefer {@link DomainEvents.PractitionerScheduleChanged}. Kept as a type alias for any
 *     leftover module-local listeners during T14.
 */
@Deprecated
public record PractitionerScheduleChanged(
    UUID practitionerRoleId,
    UUID practitionerId,
    UUID hospitalId,
    List<UUID> affectedBookedSlotIds,
    Instant at) {

  public PractitionerScheduleChanged {
    affectedBookedSlotIds =
        affectedBookedSlotIds == null ? List.of() : List.copyOf(affectedBookedSlotIds);
  }

  public DomainEvents.PractitionerScheduleChanged toShared() {
    return new DomainEvents.PractitionerScheduleChanged(
        practitionerRoleId, practitionerId, hospitalId, affectedBookedSlotIds, at);
  }
}
