package com.mediflow.notification.service;

import com.mediflow.notification.domain.Channel;
import com.mediflow.notification.domain.NotificationDelivery;
import com.mediflow.notification.domain.NotificationEventType;
import com.mediflow.notification.domain.Priority;
import com.mediflow.notification.repo.NotificationDeliveryRepository;
import com.mediflow.shared.domain.Notification;
import com.mediflow.shared.domain.NotificationStatus;
import com.mediflow.shared.repo.NotificationRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists a {@code notification} row plus its {@code notification_delivery} metadata. */
@Component
public class NotificationWriter {

  private final NotificationRepository notifications;
  private final NotificationDeliveryRepository deliveries;

  public NotificationWriter(
      NotificationRepository notifications, NotificationDeliveryRepository deliveries) {
    this.notifications = notifications;
    this.deliveries = deliveries;
  }

  /** @param rootId null for a fresh notification; the chain root for an escalation */
  @Transactional(propagation = Propagation.MANDATORY)
  public Notification create(
      UUID recipientUserId,
      UUID patientId,
      Channel channel,
      String locale,
      String subject,
      String body,
      NotificationEventType type,
      Priority priority,
      UUID rootId) {
    Notification n = new Notification();
    n.setRecipientUserId(recipientUserId);
    n.setPatientId(patientId);
    n.setChannel(channel.name());
    n.setLocale(locale);
    n.setSubject(subject == null || subject.length() <= 255 ? subject : subject.substring(0, 255));
    n.setBody(body == null ? "" : body);
    n.setStatus(NotificationStatus.PENDING);
    n = notifications.saveAndFlush(n);
    NotificationDelivery d = new NotificationDelivery();
    d.setNotificationId(n.getId());
    d.setRootNotificationId(rootId == null ? n.getId() : rootId);
    d.setEventType(type);
    d.setPriority(priority);
    deliveries.save(d);
    return n;
  }
}
