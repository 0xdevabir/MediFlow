package com.mediflow.appointment.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Where M2 gets free slots from. T03 (directory) owns slot management; until its {@code
 * DirectoryQueryService} is wired, {@link JpaSlotCatalog} reads the shared tables directly.
 */
public interface SlotCatalog {

  /** Free (not full) slots starting in [from, to), optionally narrowed by hospital/doctor. */
  List<FreeSlot> freeSlots(UUID hospitalId, UUID doctorId, Instant from, Instant to);

  /** The slot with labels, full or not. */
  Optional<FreeSlot> find(UUID slotId);
}
