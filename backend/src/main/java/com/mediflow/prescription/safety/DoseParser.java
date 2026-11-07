package com.mediflow.prescription.safety;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts a numeric dose and unit from free-text like {@code 500 mg}, {@code 5mg/kg}. */
public final class DoseParser {

  private static final Pattern DOSE =
      Pattern.compile(
          "(?i)(\\d+(?:[.,]\\d+)?)\\s*(mg(?:\\s*/\\s*kg)?(?:\\s*/\\s*day)?|g|mcg|µg|ml|iu)\\b");

  private DoseParser() {}

  public record ParsedDose(BigDecimal amount, String unit) {}

  public static Optional<ParsedDose> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    Matcher m = DOSE.matcher(raw.trim());
    if (!m.find()) {
      return Optional.empty();
    }
    String num = m.group(1).replace(',', '.');
    String unit = m.group(2).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    unit = unit.replace("µg", "mcg");
    try {
      return Optional.of(new ParsedDose(new BigDecimal(num), unit));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  /** How many times per day for common abbreviations (OD/BD/TDS/QID/HS → 1..4). */
  public static int timesPerDay(String frequency) {
    if (frequency == null || frequency.isBlank()) {
      return 1;
    }
    String f = frequency.trim().toLowerCase(Locale.ROOT);
    if (f.matches("(?i)od|once\\s*daily|1\\+0\\+0|q24h|hs|nocte")) {
      return 1;
    }
    if (f.matches("(?i)bd|twice\\s*daily|1\\+0\\+1|q12h")) {
      return 2;
    }
    if (f.matches("(?i)tds|tid|three\\s*times|1\\+1\\+1|q8h")) {
      return 3;
    }
    if (f.matches("(?i)qid|four\\s*times|1\\+1\\+1\\+1|q6h")) {
      return 4;
    }
    Matcher plus = Pattern.compile("(\\d+)\\s*\\+\\s*(\\d+)\\s*\\+\\s*(\\d+)(?:\\s*\\+\\s*(\\d+))?")
        .matcher(f);
    if (plus.matches()) {
      int sum = Integer.parseInt(plus.group(1))
          + Integer.parseInt(plus.group(2))
          + Integer.parseInt(plus.group(3));
      if (plus.group(4) != null) {
        sum += Integer.parseInt(plus.group(4));
      }
      return Math.max(sum, 1);
    }
    return 1;
  }
}
