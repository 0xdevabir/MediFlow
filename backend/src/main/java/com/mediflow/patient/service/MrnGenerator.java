package com.mediflow.patient.service;

import com.mediflow.shared.port.ClockPort;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Issues MRNs {@code MF-YYYY-NNNNNN} from an atomic per-year counter (Asia/Dhaka year). */
@Component
public class MrnGenerator {

  static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

  private final JdbcTemplate jdbc;
  private final ClockPort clock;

  public MrnGenerator(JdbcTemplate jdbc, ClockPort clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public String next() {
    int year = clock.now().atZone(ZONE).getYear();
    Long value =
        jdbc.queryForObject(
            "INSERT INTO patient_mrn_counter (mrn_year, last_value) VALUES (?, 1)"
                + " ON CONFLICT (mrn_year) DO UPDATE SET last_value = patient_mrn_counter.last_value + 1"
                + " RETURNING last_value",
            Long.class,
            year);
    return format(year, value);
  }

  public static String format(int year, long value) {
    if (value > 999_999L) {
      throw new IllegalStateException("MRN counter exhausted for " + year);
    }
    return String.format("MF-%04d-%06d", year, value);
  }

  public static boolean looksLikeMrn(String s) {
    return s != null && s.trim().toUpperCase().matches("MF-\\d{4}-\\d{6}");
  }
}
