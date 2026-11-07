package com.mediflow.appointment.queue;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueOverrideLogRepository extends JpaRepository<QueueOverrideLog, UUID> {

  List<QueueOverrideLog> findByQueueEntryIdOrderByOccurredAtAsc(UUID queueEntryId);
}
