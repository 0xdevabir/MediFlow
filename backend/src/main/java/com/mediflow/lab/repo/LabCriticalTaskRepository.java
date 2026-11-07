package com.mediflow.lab.repo;

import com.mediflow.lab.domain.LabCriticalTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabCriticalTaskRepository extends JpaRepository<LabCriticalTask, UUID> {

  List<LabCriticalTask> findByAssigneeIdAndStatusOrderByCreatedAtAsc(
      UUID assigneeId, LabCriticalTask.Status status);

  List<LabCriticalTask> findByStatusOrderByCreatedAtAsc(LabCriticalTask.Status status);

  Optional<LabCriticalTask> findByReportId(UUID reportId);
}
