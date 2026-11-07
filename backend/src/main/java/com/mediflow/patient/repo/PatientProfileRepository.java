package com.mediflow.patient.repo;

import com.mediflow.patient.domain.PatientProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientProfileRepository extends JpaRepository<PatientProfile, UUID> {

  Optional<PatientProfile> findByPatientId(UUID patientId);

  Optional<PatientProfile> findByNationalIdHash(String nationalIdHash);

  List<PatientProfile> findByIdentityHash(String identityHash);
}
