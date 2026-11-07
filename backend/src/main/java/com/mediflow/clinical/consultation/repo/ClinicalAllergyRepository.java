package com.mediflow.clinical.consultation.repo;

import com.mediflow.shared.domain.Allergy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalAllergyRepository extends JpaRepository<Allergy, UUID> {

  List<Allergy> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
