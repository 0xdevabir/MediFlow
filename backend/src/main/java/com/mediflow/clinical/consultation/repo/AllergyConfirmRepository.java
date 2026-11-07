package com.mediflow.clinical.consultation.repo;

import com.mediflow.clinical.consultation.domain.AllergyConfirmEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllergyConfirmRepository extends JpaRepository<AllergyConfirmEntity, UUID> {

  Optional<AllergyConfirmEntity> findByAllergyId(UUID allergyId);

  List<AllergyConfirmEntity> findByPatientId(UUID patientId);

  List<AllergyConfirmEntity> findByAllergyIdIn(Collection<UUID> allergyIds);
}
