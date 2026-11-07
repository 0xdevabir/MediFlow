package com.mediflow.directory.service;

import com.mediflow.directory.config.DirectoryProperties;
import com.mediflow.directory.repo.DirectorySlotRepository;
import com.mediflow.directory.schedule.InvalidScheduleException;
import com.mediflow.directory.schedule.SlotPlanner;
import com.mediflow.directory.schedule.SlotPlanner.PlannedSlot;
import com.mediflow.directory.schedule.WeeklySchedule;
import com.mediflow.shared.domain.PractitionerRoleEntity;
import com.mediflow.shared.domain.Slot;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.PractitionerRoleEntityRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materialises {@code slot} rows from {@code practitioner_role.schedule} for the next N days
 * (default 14) and reconciles existing future slots with the current template.
 *
 * <p>Rules: never duplicates (unique index + ON CONFLICT); never touches past slots; never deletes
 * or shrinks a booked slot. Unbooked slots that no longer fit the template are deleted, or closed
 * (capacity 0) if an appointment row still references them. Booked slots that no longer fit are
 * kept and reported in {@link Result#affectedBookedSlotIds()}.
 *
 * <p>Internal service (no {@code @PreAuthorize}): called by the scheduler and by {@link
 * DirectoryAdminService}, which is admin-only.
 */
@Service
public class SlotGenerationService {

  private static final Logger log = LoggerFactory.getLogger(SlotGenerationService.class);

  private final PractitionerRoleEntityRepository roles;
  private final DirectorySlotRepository slots;
  private final ClockPort clock;
  private final DirectoryProperties props;

  public SlotGenerationService(
      PractitionerRoleEntityRepository roles,
      DirectorySlotRepository slots,
      ClockPort clock,
      DirectoryProperties props) {
    this.roles = roles;
    this.slots = slots;
    this.clock = clock;
    this.props = props;
  }

  public record Result(
      int created, int updated, int removed, int closed, List<UUID> affectedBookedSlotIds) {}

  /** Reconciles every practitioner role (active ones get slots, inactive ones lose free slots). */
  @Transactional
  public int reconcileAll() {
    int created = 0;
    for (PractitionerRoleEntity role : roles.findAll()) {
      try {
        created += reconcile(role).created();
      } catch (InvalidScheduleException e) {
        log.warn("Skipping practitioner_role {}: invalid schedule ({})", role.getId(), e.getMessageKey());
      }
    }
    return created;
  }

  @Transactional
  public Result reconcile(UUID practitionerRoleId) {
    PractitionerRoleEntity role =
        roles
            .findById(practitionerRoleId)
            .orElseThrow(() -> new DirectoryValidationException("directory.error.notFound"));
    return reconcile(role);
  }

  @Transactional
  public Result reconcile(PractitionerRoleEntity role) {
    ZoneId zone = props.zoneId();
    Instant now = clock.now();
    LocalDate today = LocalDate.ofInstant(now, zone);
    int horizon = Math.max(1, props.slotHorizonDays());
    Instant windowEnd = today.plusDays(horizon).atStartOfDay(zone).toInstant();

    WeeklySchedule schedule =
        role.getActive() ? WeeklySchedule.parse(role.getSchedule()) : WeeklySchedule.empty();
    List<PlannedSlot> planned = SlotPlanner.plan(schedule, today, horizon, zone, now);
    Map<Instant, PlannedSlot> desired = new HashMap<>();
    for (PlannedSlot p : planned) {
      desired.put(p.startsAt(), p);
    }

    int updated = 0;
    int removed = 0;
    int closed = 0;
    List<UUID> affected = new ArrayList<>();
    // existing future slots (strictly after now) inside the horizon
    for (Slot s : slots.findInRange(role.getId(), now.plusNanos(1), windowEnd)) {
      PlannedSlot want = desired.remove(s.getStartsAt());
      boolean booked = s.getBookedCount() > 0;
      if (want != null) {
        if (want.endsAt().equals(s.getEndsAt())) {
          int newCap = Math.max(want.capacity(), s.getBookedCount());
          if (newCap != s.getCapacity()) {
            s.setCapacity(newCap);
            updated++;
          }
        } else if (!booked) {
          s.setEndsAt(want.endsAt());
          s.setCapacity(want.capacity());
          updated++;
        } else {
          affected.add(s.getId()); // booked slot whose length changed: keep as is
        }
      } else if (booked) {
        affected.add(s.getId());
      } else if (slots.isReferencedByAppointment(s.getId())) {
        if (s.getCapacity() != 0) {
          s.setCapacity(0);
          closed++;
        }
      } else {
        slots.delete(s);
        removed++;
      }
    }
    slots.flush();

    int created = 0;
    for (PlannedSlot p : planned) {
      if (desired.containsKey(p.startsAt())) {
        created += slots.insertIfAbsent(role.getId(), p.startsAt(), p.endsAt(), p.capacity());
      }
    }
    if (created + updated + removed + closed > 0 || !affected.isEmpty()) {
      log.info(
          "Slots for practitioner_role {}: +{} ~{} -{} closed {} affected-booked {}",
          role.getId(), created, updated, removed, closed, affected.size());
    }
    return new Result(created, updated, removed, closed, affected);
  }
}
