package com.mediflow.notification.config;

import com.mediflow.notification.service.ReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Periodically sends due reminders. Disable with {@code mediflow.notification.scheduler.enabled=false}. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "mediflow.notification.scheduler.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NotificationSchedulingConfig {

  private static final Logger log = LoggerFactory.getLogger(NotificationSchedulingConfig.class);

  private final ReminderService reminders;

  public NotificationSchedulingConfig(ReminderService reminders) {
    this.reminders = reminders;
  }

  @Scheduled(
      initialDelayString = "${mediflow.notification.scheduler.interval-ms:60000}",
      fixedDelayString = "${mediflow.notification.scheduler.interval-ms:60000}")
  public void sendDueReminders() {
    try {
      int n = reminders.dispatchDue();
      if (n > 0) {
        log.info("Sent {} due reminder(s)", n);
      }
    } catch (RuntimeException e) {
      log.error("Reminder dispatch failed", e);
    }
  }
}
