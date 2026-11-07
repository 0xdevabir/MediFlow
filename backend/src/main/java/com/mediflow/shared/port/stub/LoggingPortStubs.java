package com.mediflow.shared.port.stub;

import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import com.mediflow.shared.port.ObjectStoragePort;
import com.mediflow.shared.port.PatientAccessPort;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoggingPortStubs {

  private static final Logger log = LoggerFactory.getLogger(LoggingPortStubs.class);

  @Bean
  @ConditionalOnMissingBean(AuditPort.class)
  AuditPort auditPort() {
    return (actorUserId, patientId, action, resourceType, resourceId, details) ->
        log.info(
            "AUDIT stub action={} actor={} patient={} resource={}/{} details={}",
            action,
            actorUserId,
            patientId,
            resourceType,
            resourceId,
            details == null ? Map.of() : details);
  }

  @Bean
  @ConditionalOnMissingBean(NotificationPort.class)
  NotificationPort notificationPort() {
    return new NotificationPort() {
      @Override
      public void notifyUser(
          UUID recipientUserId, String channel, String locale, String subject, String body) {
        log.info(
            "NOTIFY stub user={} channel={} locale={} subject={}",
            recipientUserId,
            channel,
            locale,
            subject);
      }

      @Override
      public void notifyPatient(
          UUID patientId, String channel, String locale, String subject, String body) {
        log.info(
            "NOTIFY stub patient={} channel={} locale={} subject={}",
            patientId,
            channel,
            locale,
            subject);
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean(PatientAccessPort.class)
  PatientAccessPort patientAccessPort() {
    return (doctorId, patientId) -> {
      log.debug("PatientAccess stub deny-by-default doctor={} patient={}", doctorId, patientId);
      return false;
    };
  }

  @Bean
  @ConditionalOnMissingBean(ObjectStoragePort.class)
  ObjectStoragePort objectStoragePort() {
    Map<String, byte[]> store = new ConcurrentHashMap<>();
    return new ObjectStoragePort() {
      @Override
      public void put(String objectKey, InputStream data, long contentLength, String contentType) {
        try {
          store.put(objectKey, data.readAllBytes());
          log.info("STORAGE stub put key={} type={} bytes={}", objectKey, contentType, contentLength);
        } catch (Exception e) {
          throw new IllegalStateException("Failed to store object " + objectKey, e);
        }
      }

      @Override
      public Optional<InputStream> get(String objectKey) {
        byte[] bytes = store.get(objectKey);
        return bytes == null ? Optional.empty() : Optional.of(new ByteArrayInputStream(bytes));
      }

      @Override
      public void delete(String objectKey) {
        store.remove(objectKey);
        log.info("STORAGE stub delete key={}", objectKey);
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean(ClockPort.class)
  ClockPort clockPort() {
    return Instant::now;
  }
}
