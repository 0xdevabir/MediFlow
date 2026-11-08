package com.mediflow.shared.repo;

import com.mediflow.shared.domain.Patient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

  Optional<Patient> findByUserId(UUID userId);
}

