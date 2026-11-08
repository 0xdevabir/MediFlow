package com.mediflow.clinical.consultation.repo;

import com.mediflow.clinical.consultation.domain.FollowUpEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowUpRepository extends JpaRepository<FollowUpEntity, UUID> {

  Optional<FollowUpEntity> findByEncounterId(UUID encounterId);
}
