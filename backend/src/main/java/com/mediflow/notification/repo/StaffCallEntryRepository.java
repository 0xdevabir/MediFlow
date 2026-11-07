package com.mediflow.notification.repo;

import com.mediflow.notification.domain.StaffCallEntry;
import com.mediflow.notification.domain.StaffCallStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffCallEntryRepository extends JpaRepository<StaffCallEntry, UUID> {

  List<StaffCallEntry> findByStatusOrderByPriorityDescCreatedAtAsc(StaffCallStatus status);

  Optional<StaffCallEntry> findFirstByRootNotificationIdAndStatus(
      UUID rootNotificationId, StaffCallStatus status);

  long countByStatus(StaffCallStatus status);
}
