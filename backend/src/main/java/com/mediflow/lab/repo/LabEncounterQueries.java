package com.mediflow.lab.repo;

import com.mediflow.shared.domain.Encounter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Encounter lookup for doctor-ordered labs. */
public interface LabEncounterQueries extends Repository<Encounter, UUID> {

  Optional<Encounter> findById(UUID id);
}
