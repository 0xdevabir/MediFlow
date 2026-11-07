package com.mediflow.notification.repo;

import com.mediflow.shared.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-side queries over the T00 {@code notification} table for the in-app inbox. */
public interface InAppNotificationRepository extends Repository<Notification, UUID> {

  @Query(
      "select n from Notification n where n.id = :id and n.recipientUserId = :userId and n.channel = 'IN_APP'")
  java.util.Optional<Notification> findOwnInApp(@Param("id") UUID id, @Param("userId") UUID userId);

  @Query(
      "select n from Notification n where n.recipientUserId = :userId and n.channel = 'IN_APP'"
          + " order by n.createdAt desc")
  List<Notification> findInbox(@Param("userId") UUID userId, Pageable page);

  @Query(
      "select count(n) from Notification n, NotificationDelivery d where d.notificationId = n.id"
          + " and n.recipientUserId = :userId and n.channel = 'IN_APP' and d.readAt is null")
  long countUnread(@Param("userId") UUID userId);

  @Query(
      "select d from Notification n, NotificationDelivery d where d.notificationId = n.id"
          + " and n.recipientUserId = :userId and n.channel = 'IN_APP' and d.readAt is null")
  List<com.mediflow.notification.domain.NotificationDelivery> findUnreadDeliveries(
      @Param("userId") UUID userId);

  @Query(
      "select n from Notification n where n.patientId = :patientId order by n.createdAt desc")
  List<Notification> findByPatient(@Param("patientId") UUID patientId, Pageable page);
}
