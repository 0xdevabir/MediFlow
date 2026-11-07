package com.mediflow.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediflow.directory.schedule.InvalidScheduleException;
import com.mediflow.directory.schedule.SlotPlanner;
import com.mediflow.directory.schedule.SlotPlanner.PlannedSlot;
import com.mediflow.directory.schedule.WeeklySchedule;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WeeklyScheduleAndPlannerTest {

  private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
  // 2026-11-09 is a Monday
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

  @Test
  void parsesFullAndShortDayNamesAndDefaultsCapacity() {
    WeeklySchedule s =
        WeeklySchedule.parse(
            "[{\"day\":\"mon\",\"start\":\"09:00\",\"end\":\"10:00\",\"slotMinutes\":15},"
                + "{\"day\":\"WEDNESDAY\",\"start\":\"14:00\",\"end\":\"16:00\",\"slotMinutes\":30,\"capacity\":2}]");
    assertThat(s.windows()).hasSize(2);
    assertThat(s.windowsFor(DayOfWeek.MONDAY).get(0).capacity()).isEqualTo(1);
    assertThat(s.windowsFor(DayOfWeek.WEDNESDAY).get(0).capacity()).isEqualTo(2);
    // canonical JSON round-trips
    assertThat(WeeklySchedule.parse(s.toJson())).isEqualTo(s);
  }

  @Test
  void blankIsEmpty() {
    assertThat(WeeklySchedule.parse(null).isEmpty()).isTrue();
    assertThat(WeeklySchedule.parse("  ").isEmpty()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not json",
        "{\"day\":\"MON\"}",
        "[{\"day\":\"FUNDAY\",\"start\":\"09:00\",\"end\":\"10:00\",\"slotMinutes\":15}]",
        "[{\"day\":\"MON\",\"start\":\"10:00\",\"end\":\"09:00\",\"slotMinutes\":15}]",
        "[{\"day\":\"MON\",\"start\":\"9am\",\"end\":\"10:00\",\"slotMinutes\":15}]",
        "[{\"day\":\"MON\",\"start\":\"09:00\",\"end\":\"10:00\",\"slotMinutes\":1}]",
        "[{\"day\":\"MON\",\"start\":\"09:00\",\"end\":\"10:00\",\"slotMinutes\":15,\"capacity\":0}]",
        "[{\"day\":\"MON\",\"start\":\"09:00\",\"end\":\"11:00\",\"slotMinutes\":15},"
            + "{\"day\":\"MON\",\"start\":\"10:30\",\"end\":\"12:00\",\"slotMinutes\":15}]"
      })
  void rejectsInvalidTemplates(String json) {
    assertThatThrownBy(() -> WeeklySchedule.parse(json)).isInstanceOf(InvalidScheduleException.class);
  }

  @Test
  void plansOnlyScheduledDaysAndWholeSlots() {
    // 09:00-10:10 with 20-minute slots → 09:00, 09:20, 09:40 (10:00-10:20 would overrun)
    WeeklySchedule s =
        WeeklySchedule.parse(
            "[{\"day\":\"MON\",\"start\":\"09:00\",\"end\":\"10:10\",\"slotMinutes\":20}]");
    List<PlannedSlot> slots = SlotPlanner.plan(s, MONDAY, 14, DHAKA, null);
    assertThat(slots).hasSize(6); // two Mondays x 3
    for (PlannedSlot p : slots) {
      ZonedDateTime z = p.startsAt().atZone(DHAKA);
      assertThat(z.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
      assertThat(z.getHour()).isEqualTo(9);
      assertThat(p.endsAt()).isEqualTo(p.startsAt().plusSeconds(20 * 60));
    }
    assertThat(new HashSet<>(slots.stream().map(PlannedSlot::startsAt).toList())).hasSize(6);
  }

  @Test
  void skipsSlotsNotAfterNow() {
    WeeklySchedule s =
        WeeklySchedule.parse(
            "[{\"day\":\"MON\",\"start\":\"09:00\",\"end\":\"10:00\",\"slotMinutes\":15}]");
    Instant now = ZonedDateTime.of(MONDAY.atTime(9, 15), DHAKA).toInstant();
    List<PlannedSlot> slots = SlotPlanner.plan(s, MONDAY, 1, DHAKA, now);
    assertThat(slots).extracting(p -> p.startsAt().atZone(DHAKA).getMinute()).containsExactly(30, 45);
  }

  @Test
  void horizonBoundsTheRange() {
    WeeklySchedule s =
        WeeklySchedule.parse(
            "[{\"day\":\"MON\",\"start\":\"09:00\",\"end\":\"09:30\",\"slotMinutes\":15},"
                + "{\"day\":\"TUE\",\"start\":\"09:00\",\"end\":\"09:30\",\"slotMinutes\":15}]");
    assertThat(SlotPlanner.plan(s, MONDAY, 1, DHAKA, null)).hasSize(2);
    assertThat(SlotPlanner.plan(s, MONDAY, 0, DHAKA, null)).isEmpty();
    assertThat(SlotPlanner.plan(WeeklySchedule.empty(), MONDAY, 14, DHAKA, null)).isEmpty();
  }
}
