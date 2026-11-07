package com.mediflow.patient.repo;

import com.mediflow.patient.domain.PatientAllergy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, UUID> {

  List<PatientAllergy> findByPatientIdOrderByCreatedAtAsc(UUID patientId);
}
