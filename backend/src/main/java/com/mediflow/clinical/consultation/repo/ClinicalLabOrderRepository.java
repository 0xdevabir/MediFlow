package com.mediflow.clinical.consultation.repo;

import com.mediflow.shared.domain.LabOrder;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalLabOrderRepository extends JpaRepository<LabOrder, UUID> {

  List<LabOrder> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
