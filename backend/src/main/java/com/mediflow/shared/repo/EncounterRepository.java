package com.mediflow.shared.repo;

import com.mediflow.shared.domain.Encounter;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncounterRepository extends JpaRepository<Encounter, UUID> {
}
