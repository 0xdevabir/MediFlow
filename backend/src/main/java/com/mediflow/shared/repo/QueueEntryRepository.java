package com.mediflow.shared.repo;

import com.mediflow.shared.domain.QueueEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {
}
