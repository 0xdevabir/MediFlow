package com.mediflow.directory.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code mediflow.directory.*} — all optional (no application.yml change needed).
 *
 * @param zone local time zone of the weekly schedules (default Asia/Dhaka)
 * @param slotHorizonDays how many days ahead slots are materialised (default 14)
 * @param slotCron cron of the nightly slot materialisation job
 * @param generateOnStartup materialise slots once when the application is ready
 */
@ConfigurationProperties(prefix = "mediflow.directory")
public record DirectoryProperties(
    @DefaultValue("Asia/Dhaka") String zone,
    @DefaultValue("14") int slotHorizonDays,
    @DefaultValue("0 15 2 * * *") String slotCron,
    @DefaultValue("true") boolean generateOnStartup) {

  public ZoneId zoneId() {
    return ZoneId.of(zone);
  }
}
