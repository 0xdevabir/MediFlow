package com.mediflow.appointment.support;

import com.mediflow.shared.domain.Patient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Read-only patient lookups needed by M2 (booking on behalf, check-in identity, summaries). */
public interface ApptPatientRepository extends Repository<Patient, UUID> {

  Optional<Patient> findById(UUID id);

  Optional<Patient> findByUserId(UUID userId);

  Optional<Patient> findByMrn(String mrn);
}
