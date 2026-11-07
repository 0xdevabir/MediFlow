package com.mediflow.prescription.repo;

import com.mediflow.shared.domain.AiJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RxAiJobQueries extends JpaRepository<AiJob, UUID> {
  Optional<AiJob> findByJobId(UUID jobId);
}
