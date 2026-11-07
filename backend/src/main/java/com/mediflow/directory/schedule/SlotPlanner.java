package com.mediflow.directory.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure function: weekly template → concrete slot windows for a date range. */
public final class SlotPlanner {

  private SlotPlanner() {}

  public record PlannedSlot(Instant startsAt, Instant endsAt, int capacity) {}

  /**
   * Plans slots for {@code days} consecutive local dates starting at {@code from} (in {@code
   * zone}). Only whole slots that fit inside a window are produced; slots starting at or before
   * {@code notBefore} are skipped. Result is ordered by start and has unique start instants.
   */
  public static List<PlannedSlot> plan(
      WeeklySchedule schedule, LocalDate from, int days, ZoneId zone, Instant notBefore) {
    Map<Instant, PlannedSlot> byStart = new LinkedHashMap<>();
    if (schedule == null || schedule.isEmpty() || days <= 0) {
      return List.of();
    }
    for (int i = 0; i < days; i++) {
      LocalDate date = from.plusDays(i);
      for (WeeklySchedule.Window w : schedule.windowsFor(date.getDayOfWeek())) {
        LocalTime t = w.start();
        while (true) {
          LocalTime next = t.plusMinutes(w.slotMinutes());
          // stop when the slot would pass the window end or wrap past midnight
          if (next.isAfter(w.end()) || !next.isAfter(t)) {
            break;
          }
          Instant start = ZonedDateTime.of(date, t, zone).toInstant();
          Instant end = ZonedDateTime.of(date, next, zone).toInstant();
          if (notBefore == null || start.isAfter(notBefore)) {
            byStart.putIfAbsent(start, new PlannedSlot(start, end, w.capacity()));
          }
          t = next;
        }
      }
    }
    List<PlannedSlot> out = new ArrayList<>(byStart.values());
    out.sort((a, b) -> a.startsAt().compareTo(b.startsAt()));
    return out;
  }
}
