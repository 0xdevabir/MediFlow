package com.mediflow.prescription.repo;

import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.Patient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RxEncounterQueries extends JpaRepository<Encounter, UUID> {

  @Query("select p from Patient p where p.id = :id")
  Optional<Patient> patient(@Param("id") UUID id);
}
