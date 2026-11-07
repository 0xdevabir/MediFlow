package com.mediflow.shared.repo;

import com.mediflow.shared.domain.Reminder;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {
}
