package com.mediflow.appointment.booking;

import com.mediflow.shared.domain.Slot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ApptSlotQueryRepository extends Repository<Slot, UUID> {

  String SELECT =
      "select new com.mediflow.appointment.booking.FreeSlot(s.id, s.startsAt, s.endsAt,"
          + " s.capacity, s.bookedCount, pr.id, p.id, p.name, h.id, h.name, d.id, d.name)"
          + " from Slot s, PractitionerRoleEntity pr, Practitioner p, Hospital h, Department d"
          + " where pr.id = s.practitionerRoleId and p.id = pr.practitionerId"
          + " and h.id = pr.hospitalId and d.id = pr.departmentId";

  @Query(
      SELECT
          + " and pr.active = true and h.active = true"
          + " and s.startsAt >= :from and s.startsAt < :to and s.bookedCount < s.capacity"
          + " order by s.startsAt, p.name")
  List<FreeSlot> findFree(@Param("from") Instant from, @Param("to") Instant to);

  @Query(SELECT + " and s.id = :id")
  Optional<FreeSlot> findView(@Param("id") UUID id);
}
