package com.mediflow.appointment.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** OPD days are counted in the hospital's local zone (Bangladesh). */
public final class QueueClock {

  public static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

  private QueueClock() {}

  public static LocalDate queueDate(Instant instant) {
    return instant.atZone(ZONE).toLocalDate();
  }
}
