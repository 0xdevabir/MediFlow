package com.mediflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.notification.service.DoseScheduleCalculator;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class DoseScheduleCalculatorTest {

  private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

  /** 2026-11-07 07:00 Dhaka: before the first 08:00 slot. */
  private static final Instant EARLY = ZonedDateTime.of(2026, 11, 7, 7, 0, 0, 0, DHAKA).toInstant();

  @ParameterizedTest(name = "{0} x {1} -> {2} doses")
  @CsvSource({
    "OD, 5 days, 5",
    "OD, 1 week, 7",
    "BD, 7 days, 14",
    "BD, 2 weeks, 28",
    "TDS, 3 days, 9",
    "TDS, 1 month, 90",
    "QID, 2 days, 8",
    "1+0+1, 5 days, 10",
    "1+1+1, ৫ দিন, 15",
    "Q8H, 2d, 6",
    "দিনে ২ বার, ৩ দিন, 6",
    "BD, continue, 60"
  })
  void doseCountIsDosesPerDayTimesDays(String freq, String duration, int expected) {
    assertThat(DoseScheduleCalculator.schedule(freq, duration, EARLY, DHAKA)).hasSize(expected);
  }

  @Test
  void bdStartsAtNextSlotAndKeepsTotal() {
    Instant afterMorning = ZonedDateTime.of(2026, 11, 7, 9, 30, 0, 0, DHAKA).toInstant();
    List<Instant> doses = DoseScheduleCalculator.schedule("BD", "2 days", afterMorning, DHAKA);
    assertThat(doses).hasSize(4);
    assertThat(doses.get(0).atZone(DHAKA)).isEqualTo(ZonedDateTime.of(2026, 11, 7, 20, 0, 0, 0, DHAKA));
    assertThat(doses.get(3).atZone(DHAKA)).isEqualTo(ZonedDateTime.of(2026, 11, 9, 8, 0, 0, 0, DHAKA));
    assertThat(doses).isSorted();
  }

  @Test
  void tdsUsesMorningNoonNight() {
    List<Instant> doses = DoseScheduleCalculator.schedule("TDS", "1 day", EARLY, DHAKA);
    assertThat(doses)
        .extracting(i -> i.atZone(DHAKA).toLocalTime())
        .containsExactly(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0));
  }

  @Test
  void bangladeshiNotationSkipsZeroPositions() {
    assertThat(DoseScheduleCalculator.dailySlots("0+0+1")).contains(List.of(LocalTime.of(20, 0)));
    assertThat(DoseScheduleCalculator.dailySlots("1+0+1"))
        .contains(List.of(LocalTime.of(8, 0), LocalTime.of(20, 0)));
  }

  @ParameterizedTest
  @CsvSource({"PRN, 5 days", "SOS, 5 days", "STAT, 1 day", "BD, ''", "BD, as advised", "'', 5 days"})
  void unschedulableInputYieldsNoReminders(String freq, String duration) {
    assertThat(DoseScheduleCalculator.schedule(freq, duration, EARLY, DHAKA)).isEmpty();
  }
}
