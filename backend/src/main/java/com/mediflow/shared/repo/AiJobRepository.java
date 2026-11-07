package com.mediflow.shared.repo;

import com.mediflow.shared.domain.AiJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiJobRepository extends JpaRepository<AiJob, UUID> {
}
