package com.mediflow.shared.port.stub;

import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.LabOrderPort;
import com.mediflow.shared.port.NotificationPort;
import com.mediflow.shared.port.ObjectStoragePort;
import com.mediflow.shared.port.PatientAccessPort;
import com.mediflow.shared.web.CurrentUser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * Fallback port implementations. Registered as an auto-configuration (see {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}) so it is
 * evaluated after all component-scanned beans: as soon as a module declares its own
 * {@code @Component} implementing a port, the stub here backs off.
 */
@AutoConfiguration
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
    return new PatientAccessPort() {
      @Override
      public boolean isAssigned(UUID doctorId, UUID patientId) {
        log.debug("PatientAccess stub deny-by-default doctor={} patient={}", doctorId, patientId);
        return false;
      }

      @Override
      public boolean isErAssigned(UUID erUserId, UUID patientId) {
        log.debug("PatientAccess stub ER deny-by-default er={} patient={}", erUserId, patientId);
        return false;
      }
    };
  }

  /**
   * Boot-only {@link LabOrderPort} until V2-08. Requires {@link CurrentUser} (always present in the
   * app). Omitted from slim context runners that do not register CurrentUser.
   */
  @Bean
  @ConditionalOnMissingBean(LabOrderPort.class)
  @ConditionalOnBean({CurrentUser.class, ClockPort.class})
  LabOrderPort labOrderPort(
      ApplicationEventPublisher events, CurrentUser currentUser, ClockPort clock) {
    return new NoOpLabOrderPort(events, currentUser, clock);
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
          log.info(
              "STORAGE stub put key={} type={} bytes={}", objectKey, contentType, contentLength);
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
