package com.mediflow.notification.config;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code mediflow.notification.*}. All values have safe defaults so no {@code application*.yml}
 * change is required; secrets come from env vars via relaxed binding ({@code
 * MEDIFLOW_NOTIFICATION_SMS_APIKEY}, {@code MEDIFLOW_NOTIFICATION_PUSH_ACCESSTOKEN}).
 */
@ConfigurationProperties("mediflow.notification")
public record NotificationProperties(
    /* auto = RabbitMQ when a ConnectionFactory exists, else in-memory; or force sync / rabbit. */
    @DefaultValue("auto") String transport,
    @DefaultValue("3") int maxAttempts,
    @DefaultValue("2s") Duration initialBackoff,
    @DefaultValue("2.0") double backoffMultiplier,
    @DefaultValue("5m") Duration maxBackoff,
    @DefaultValue("Asia/Dhaka") ZoneId zone,
    @DefaultValue("no-reply@mediflow.local") String mailFrom,
    @DefaultValue Sms sms,
    @DefaultValue Push push,
    @DefaultValue Scheduler scheduler) {

  public record Sms(
      /* log | http */ @DefaultValue("log") String provider,
      @DefaultValue("") String url,
      @DefaultValue("") String apiKey,
      @DefaultValue("MediFlow") String senderId) {}

  public record Push(
      /* log | fcm */ @DefaultValue("log") String provider,
      @DefaultValue("") String projectId,
      @DefaultValue("") String accessToken) {}

  public record Scheduler(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("200") int batchSize) {}

  /** Delay before attempt {@code attempt + 1} after {@code attempt} failed (1-based). */
  public Duration backoffAfter(int attempt) {
    double millis = initialBackoff.toMillis() * Math.pow(backoffMultiplier, Math.max(0, attempt - 1));
    return Duration.ofMillis((long) Math.min(millis, maxBackoff.toMillis()));
  }
}
