package com.mediflow.directory.repo;

import com.mediflow.shared.domain.Slot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Directory-owned finders over the shared {@code slot} table (T00 entity, no relations). */
public interface DirectorySlotRepository extends JpaRepository<Slot, UUID> {

  @Query(
      "select s from Slot s where s.practitionerRoleId = :roleId"
          + " and s.startsAt >= :from and s.startsAt < :to order by s.startsAt")
  List<Slot> findInRange(
      @Param("roleId") UUID roleId, @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      "select s from Slot s where s.practitionerRoleId = :roleId"
          + " and s.startsAt >= :from and s.startsAt < :to and s.startsAt > :notBefore"
          + " and s.capacity > s.bookedCount order by s.startsAt")
  List<Slot> findFree(
      @Param("roleId") UUID roleId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("notBefore") Instant notBefore);

  /** Idempotent insert; returns 1 when created, 0 when a slot already starts at that instant. */
  @Modifying
  @Query(
      value =
          "INSERT INTO slot (id, practitioner_role_id, starts_at, ends_at, capacity, booked_count,"
              + " created_at, updated_at, version)"
              + " VALUES (gen_random_uuid(), :roleId, :startsAt, :endsAt, :capacity, 0, now(), now(), 0)"
              + " ON CONFLICT (practitioner_role_id, starts_at) DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("roleId") UUID roleId,
      @Param("startsAt") Instant startsAt,
      @Param("endsAt") Instant endsAt,
      @Param("capacity") int capacity);

  /** True when any appointment row (any status) still points at the slot (FK would block delete). */
  @Query(
      value = "SELECT EXISTS (SELECT 1 FROM appointment a WHERE a.slot_id = :slotId)",
      nativeQuery = true)
  boolean isReferencedByAppointment(@Param("slotId") UUID slotId);
}
