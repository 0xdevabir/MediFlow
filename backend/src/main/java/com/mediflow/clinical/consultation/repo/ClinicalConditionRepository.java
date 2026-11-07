package com.mediflow.clinical.consultation.repo;

import com.mediflow.shared.domain.ConditionDx;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalConditionRepository extends JpaRepository<ConditionDx, UUID> {

  List<ConditionDx> findByEncounterIdOrderByRecordedAtDesc(UUID encounterId);

  List<ConditionDx> findByPatientIdAndClinicalStatusOrderByRecordedAtDesc(
      UUID patientId, String clinicalStatus);
}
