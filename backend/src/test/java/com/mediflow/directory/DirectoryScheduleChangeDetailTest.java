package com.mediflow.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediflow.directory.event.DirectoryScheduleChangeDetail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DirectoryScheduleChangeDetailTest {

  @Test
  void copiesAffectedSlotsAndMapsToShared() {
    UUID roleId = UUID.randomUUID();
    UUID practitionerId = UUID.randomUUID();
    UUID hospitalId = UUID.randomUUID();
    UUID slotId = UUID.randomUUID();
    Instant at = Instant.parse("2026-11-08T02:00:00Z");

    DirectoryScheduleChangeDetail detail =
        new DirectoryScheduleChangeDetail(
            roleId, practitionerId, hospitalId, List.of(slotId), 3, 1, 2, 1, false, at);

    assertEquals(1, detail.affectedBookedCount());
    assertEquals(3, detail.slotsCreated());
    assertEquals(slotId, detail.toShared().affectedBookedSlotIds().getFirst());
    assertEquals(1, detail.toShared().slotsClosed());
    assertEquals(3, detail.toShared().slotsCreated());
    assertTrue(detail.toShared().affectedBookedSlotIds() instanceof List);
  }
}
