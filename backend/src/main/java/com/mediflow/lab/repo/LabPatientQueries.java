package com.mediflow.lab.repo;

import com.mediflow.shared.domain.Patient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Patient lookups the lab needs (own record of a signed-in patient, walk-in by MRN). */
public interface LabPatientQueries extends Repository<Patient, UUID> {

  Optional<Patient> findById(UUID id);

  Optional<Patient> findByUserId(UUID userId);

  Optional<Patient> findByMrn(String mrn);
}

