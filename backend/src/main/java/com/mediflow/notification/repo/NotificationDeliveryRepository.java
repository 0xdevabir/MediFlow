package com.mediflow.notification.repo;

import com.mediflow.notification.domain.NotificationDelivery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

  Optional<NotificationDelivery> findByNotificationId(UUID notificationId);

  List<NotificationDelivery> findByRootNotificationIdOrderByCreatedAtAsc(UUID rootNotificationId);
}
