package com.mediflow.clinical.consultation.repo;

import com.mediflow.shared.domain.Practitioner;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalPractitionerRepository extends JpaRepository<Practitioner, UUID> {

  Optional<Practitioner> findByUserId(UUID userId);
}
