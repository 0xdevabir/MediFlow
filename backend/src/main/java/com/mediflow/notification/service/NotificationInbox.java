package com.mediflow.notification.service;

import com.mediflow.notification.domain.NotificationDelivery;
import com.mediflow.notification.domain.Priority;
import com.mediflow.notification.repo.InAppNotificationRepository;
import com.mediflow.notification.repo.NotificationDeliveryRepository;
import com.mediflow.shared.domain.Notification;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.web.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app inbox. Registered as bean {@code notificationInbox} so the navbar bell fragment can call
 * {@code @notificationInbox.unreadCount()}.
 */
@Service("notificationInbox")
public class NotificationInbox {

  public static final int PAGE_SIZE = 50;

  public record InboxRow(Notification notification, boolean unread, boolean high) {}

  private final InAppNotificationRepository inApp;
  private final NotificationDeliveryRepository deliveries;
  private final CurrentUser currentUser;
  private final ClockPort clock;

  public NotificationInbox(
      InAppNotificationRepository inApp,
      NotificationDeliveryRepository deliveries,
      CurrentUser currentUser,
      ClockPort clock) {
    this.inApp = inApp;
    this.deliveries = deliveries;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  /** Unread in-app count for the signed-in user (0 when anonymous). Used by the bell. */
  @Transactional(readOnly = true)
  public long unreadCount() {
    return currentUser.userId().map(inApp::countUnread).orElse(0L);
  }

  @Transactional(readOnly = true)
  public List<InboxRow> inbox(UUID userId) {
    List<Notification> list = inApp.findInbox(userId, PageRequest.of(0, PAGE_SIZE));
    Map<UUID, NotificationDelivery> meta =
        deliveries.findByNotificationIdIn(list.stream().map(Notification::getId).toList()).stream()
            .collect(Collectors.toMap(NotificationDelivery::getNotificationId, Function.identity()));
    return list.stream()
        .map(
            n -> {
              NotificationDelivery d = meta.get(n.getId());
              return new InboxRow(
                  n, d != null && d.getReadAt() == null, d != null && d.getPriority() == Priority.HIGH);
            })
        .toList();
  }

  /** Marks one of the user's own in-app notifications read; false if it is not theirs. */
  @Transactional
  public boolean markRead(UUID userId, UUID notificationId) {
    return inApp
        .findOwnInApp(notificationId, userId)
        .flatMap(n -> deliveries.findByNotificationId(n.getId()))
        .map(
            d -> {
              if (d.getReadAt() == null) {
                d.setReadAt(clock.now());
              }
              return true;
            })
        .orElse(false);
  }

  @Transactional
  public int markAllRead(UUID userId) {
    List<NotificationDelivery> unread = inApp.findUnreadDeliveries(userId);
    unread.forEach(d -> d.setReadAt(clock.now()));
    return unread.size();
  }
}
