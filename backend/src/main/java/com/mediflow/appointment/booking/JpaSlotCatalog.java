package com.mediflow.appointment.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link SlotCatalog}: reads the shared slot / directory tables. */
@Component
public class JpaSlotCatalog implements SlotCatalog {

  private final ApptSlotQueryRepository repo;

  public JpaSlotCatalog(ApptSlotQueryRepository repo) {
    this.repo = repo;
  }

  @Override
  @Transactional(readOnly = true)
  public List<FreeSlot> freeSlots(UUID hospitalId, UUID doctorId, Instant from, Instant to) {
    return repo.findFree(from, to).stream()
        .filter(s -> hospitalId == null || hospitalId.equals(s.hospitalId()))
        .filter(s -> doctorId == null || doctorId.equals(s.doctorId()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FreeSlot> find(UUID slotId) {
    return repo.findView(slotId);
  }
}
