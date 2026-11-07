package com.mediflow.clinical.consultation.repo;

import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalPrescriptionRepository extends JpaRepository<Prescription, UUID> {

  List<Prescription> findByPatientIdAndStatusInOrderBySignedAtDesc(
      UUID patientId, Collection<PrescriptionStatus> statuses);
}
