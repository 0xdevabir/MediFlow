package com.mediflow.directory.schedule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Weekly template stored in {@code practitioner_role.schedule} as JSON:
 *
 * <pre>
 * [{"day":"MONDAY","start":"09:00","end":"13:00","slotMinutes":15,"capacity":1}, ...]
 * </pre>
 *
 * {@code day} also accepts three-letter abbreviations (MON, TUE, ...); {@code capacity} defaults
 * to 1. Windows on the same day must not overlap.
 */
public record WeeklySchedule(List<Window> windows) {

  public static final int MIN_SLOT_MINUTES = 5;
  public static final int MAX_SLOT_MINUTES = 240;
  public static final int MAX_CAPACITY = 50;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public record Window(DayOfWeek day, LocalTime start, LocalTime end, int slotMinutes, int capacity) {}

  public WeeklySchedule {
    windows = List.copyOf(windows);
  }

  public static WeeklySchedule empty() {
    return new WeeklySchedule(List.of());
  }

  public boolean isEmpty() {
    return windows.isEmpty();
  }

  /**
   * Parses and validates the JSON template. Blank input = no schedule.
   *
   * @throws InvalidScheduleException with a message key describing the first problem
   */
  public static WeeklySchedule parse(String json) {
    if (json == null || json.isBlank()) {
      return empty();
    }
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new InvalidScheduleException("directory.schedule.error.json");
    }
    if (root == null || !root.isArray()) {
      throw new InvalidScheduleException("directory.schedule.error.json");
    }
    List<Window> windows = new ArrayList<>();
    for (JsonNode n : root) {
      if (!n.isObject()) {
        throw new InvalidScheduleException("directory.schedule.error.json");
      }
      DayOfWeek day = parseDay(text(n, "day"));
      LocalTime start = parseTime(text(n, "start"));
      LocalTime end = parseTime(text(n, "end"));
      if (!n.path("slotMinutes").canConvertToInt()) {
        throw new InvalidScheduleException("directory.schedule.error.slotMinutes");
      }
      int minutes = n.path("slotMinutes").asInt();
      int capacity = n.has("capacity") ? n.path("capacity").asInt(-1) : 1;
      windows.add(new Window(day, start, end, minutes, capacity));
    }
    WeeklySchedule schedule = new WeeklySchedule(windows);
    schedule.validate();
    return schedule;
  }

  /** Validates ranges and overlaps. */
  public void validate() {
    for (Window w : windows) {
      if (w.day() == null || w.start() == null || w.end() == null) {
        throw new InvalidScheduleException("directory.schedule.error.json");
      }
      if (!w.end().isAfter(w.start())) {
        throw new InvalidScheduleException("directory.schedule.error.order");
      }
      if (w.slotMinutes() < MIN_SLOT_MINUTES || w.slotMinutes() > MAX_SLOT_MINUTES) {
        throw new InvalidScheduleException("directory.schedule.error.slotMinutes");
      }
      if (w.capacity() < 1 || w.capacity() > MAX_CAPACITY) {
        throw new InvalidScheduleException("directory.schedule.error.capacity");
      }
    }
    List<Window> sorted = new ArrayList<>(windows);
    sorted.sort(Comparator.comparing(Window::day).thenComparing(Window::start));
    for (int i = 1; i < sorted.size(); i++) {
      Window prev = sorted.get(i - 1);
      Window cur = sorted.get(i);
      if (prev.day() == cur.day() && cur.start().isBefore(prev.end())) {
        throw new InvalidScheduleException("directory.schedule.error.overlap");
      }
    }
  }

  public List<Window> windowsFor(DayOfWeek day) {
    return windows.stream()
        .filter(w -> w.day() == day)
        .sorted(Comparator.comparing(Window::start))
        .toList();
  }

  /** Canonical JSON (full day names, HH:mm). */
  public String toJson() {
    ArrayNode arr = MAPPER.createArrayNode();
    windows.stream()
        .sorted(Comparator.comparing(Window::day).thenComparing(Window::start))
        .forEach(
            w ->
                arr.addObject()
                    .put("day", w.day().name())
                    .put("start", w.start().toString())
                    .put("end", w.end().toString())
                    .put("slotMinutes", w.slotMinutes())
                    .put("capacity", w.capacity()));
    return arr.toString();
  }

  private static String text(JsonNode n, String field) {
    JsonNode v = n.get(field);
    return v == null || v.isNull() ? null : v.asText();
  }

  static DayOfWeek parseDay(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new InvalidScheduleException("directory.schedule.error.day");
    }
    String s = raw.trim().toUpperCase(Locale.ROOT);
    for (DayOfWeek d : DayOfWeek.values()) {
      if (d.name().equals(s) || d.name().substring(0, 3).equals(s)) {
        return d;
      }
    }
    throw new InvalidScheduleException("directory.schedule.error.day");
  }

  static LocalTime parseTime(String raw) {
    if (raw == null) {
      throw new InvalidScheduleException("directory.schedule.error.time");
    }
    try {
      return LocalTime.parse(raw.trim());
    } catch (DateTimeParseException e) {
      throw new InvalidScheduleException("directory.schedule.error.time");
    }
  }
}
