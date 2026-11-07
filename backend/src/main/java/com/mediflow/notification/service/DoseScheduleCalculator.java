package com.mediflow.notification.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a prescription item's frequency + duration into concrete dose times.
 *
 * <p>Frequencies: {@code OD/QD}, {@code BD/BID}, {@code TDS/TID}, {@code QID/QDS}, {@code HS}, the
 * Bangladeshi {@code 1+0+1} notation, {@code Q4H/Q6H/Q8H/Q12H/Q24H} and their Bangla equivalents.
 * {@code PRN/SOS/STAT} (as-needed or single dose) produce no reminders. Durations: {@code N day(s)},
 * {@code N week(s)}, {@code N month(s)} (30 days), Bangla words and digits; "continue" is capped at
 * {@link #CONTINUOUS_CAP_DAYS}. Unparseable input yields an empty schedule (no guessing).
 *
 * <p>The schedule starts with the first dose slot strictly after {@code start} and contains exactly
 * {@code dosesPerDay × days} doses.
 */
public final class DoseScheduleCalculator {

  public static final int CONTINUOUS_CAP_DAYS = 30;

  private static final LocalTime[] OD = {LocalTime.of(8, 0)};
  private static final LocalTime[] BD = {LocalTime.of(8, 0), LocalTime.of(20, 0)};
  private static final LocalTime[] TDS = {LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0)};
  private static final LocalTime[] QID = {
    LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(16, 0), LocalTime.of(20, 0)
  };
  private static final LocalTime[] HS = {LocalTime.of(22, 0)};

  /** Slots for "morning + noon + night" (3 positions) and "morning + noon + evening + night" (4). */
  private static final LocalTime[] PLUS3 = TDS;

  private static final LocalTime[] PLUS4 = {
    LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(18, 0), LocalTime.of(22, 0)
  };

  private static final Pattern PLUS = Pattern.compile("^([0-9½]+)(\\+[0-9½]+){1,3}$");
  private static final Pattern EVERY_HOURS = Pattern.compile("^Q(\\d{1,2})H$");
  private static final Pattern DURATION =
      Pattern.compile("(\\d+)\\s*(DAYS?|D|WEEKS?|WKS?|W|MONTHS?|MO|M|দিন|সপ্তাহ|মাস)");

  private DoseScheduleCalculator() {}

  /** Daily dose slots for a frequency, or empty when it cannot be scheduled. */
  public static Optional<List<LocalTime>> dailySlots(String frequency) {
    if (frequency == null || frequency.isBlank()) {
      return Optional.empty();
    }
    String f = normalise(frequency).replaceAll("[\\s.\\-]", "");
    LocalTime[] slots =
        switch (f) {
          case "OD", "QD", "ONCEDAILY", "ONCEADAY", "DAILY", "দিনে1বার", "দিনেএকবার" -> OD;
          case "BD", "BID", "TWICEDAILY", "TWICEADAY", "দিনে2বার", "দিনেদুইবার" -> BD;
          case "TDS", "TID", "THRICEDAILY", "THREETIMESADAY", "দিনে3বার", "দিনেতিনবার" -> TDS;
          case "QID", "QDS", "FOURTIMESADAY", "দিনে4বার", "দিনেচারবার" -> QID;
          case "HS", "NOCTE", "ATNIGHT", "রাতে" -> HS;
          default -> null;
        };
    if (slots != null) {
      return Optional.of(List.of(slots));
    }
    Matcher plus = PLUS.matcher(f);
    if (plus.matches()) {
      String[] parts = f.split("\\+");
      LocalTime[] positions = parts.length == 4 ? PLUS4 : parts.length == 3 ? PLUS3 : BD;
      List<LocalTime> result = new ArrayList<>();
      for (int i = 0; i < parts.length; i++) {
        if (!parts[i].equals("0")) {
          result.add(positions[i]);
        }
      }
      return result.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(result));
    }
    Matcher every = EVERY_HOURS.matcher(f);
    if (every.matches()) {
      int hours = Integer.parseInt(every.group(1));
      if (hours <= 0 || 24 % hours != 0) {
        return Optional.empty();
      }
      List<LocalTime> result = new ArrayList<>();
      int first = hours >= 12 ? 8 : 6;
      for (int h = 0; h < 24; h += hours) {
        result.add(LocalTime.of((first + h) % 24, 0));
      }
      result.sort(null);
      return Optional.of(List.copyOf(result));
    }
    return Optional.empty();
  }

  /** Duration in days, or empty when it cannot be parsed. */
  public static Optional<Integer> durationDays(String duration) {
    if (duration == null || duration.isBlank()) {
      return Optional.empty();
    }
    String d = normalise(duration);
    if (d.contains("CONTINUE") || d.contains("চলবে") || d.contains("ONGOING")) {
      return Optional.of(CONTINUOUS_CAP_DAYS);
    }
    Matcher m = DURATION.matcher(d);
    if (!m.find()) {
      return d.matches("\\d+") ? Optional.of(Integer.parseInt(d)).filter(n -> n > 0) : Optional.empty();
    }
    int n = Integer.parseInt(m.group(1));
    String unit = m.group(2);
    int days =
        switch (unit) {
          case "WEEK", "WEEKS", "WK", "WKS", "W", "সপ্তাহ" -> n * 7;
          case "MONTH", "MONTHS", "MO", "M", "মাস" -> n * 30;
          default -> n;
        };
    return days > 0 ? Optional.of(Math.min(days, 366)) : Optional.empty();
  }

  /** Concrete dose instants, empty if frequency or duration cannot be scheduled. */
  public static List<Instant> schedule(String frequency, String duration, Instant start, ZoneId zone) {
    Optional<List<LocalTime>> slots = dailySlots(frequency);
    Optional<Integer> days = durationDays(duration);
    if (slots.isEmpty() || days.isEmpty()) {
      return List.of();
    }
    int total = slots.get().size() * days.get();
    List<Instant> result = new ArrayList<>(total);
    LocalDate day = start.atZone(zone).toLocalDate();
    while (result.size() < total) {
      for (LocalTime slot : slots.get()) {
        Instant at = ZonedDateTime.of(day, slot, zone).toInstant();
        if (at.isAfter(start) && result.size() < total) {
          result.add(at);
        }
      }
      day = day.plusDays(1);
    }
    return List.copyOf(result);
  }

  private static String normalise(String s) {
    StringBuilder b = new StringBuilder(s.length());
    for (char c : s.trim().toCharArray()) {
      // Bangla digits ০-৯ → ASCII
      b.append(c >= '০' && c <= '৯' ? (char) ('0' + (c - '০')) : c);
    }
    return b.toString().toUpperCase(Locale.ROOT);
  }
}
