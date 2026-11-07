package com.mediflow.lab.service;

import java.time.ZoneId;
import java.util.List;

/** Sample-collection time windows offered for direct tests. */
public final class LabSlots {

  public static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Dhaka");

  /** Furthest day ahead a direct test can be booked. */
  public static final int MAX_DAYS_AHEAD = 30;

  public static final List<String> WINDOWS =
      List.of("08:00-10:00", "10:00-12:00", "12:00-14:00", "14:00-16:00", "16:00-18:00");

  private LabSlots() {}
}
