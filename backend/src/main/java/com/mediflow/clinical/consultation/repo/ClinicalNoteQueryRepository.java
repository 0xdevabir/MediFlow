package com.mediflow.clinical.consultation.repo;

import com.mediflow.shared.domain.ClinicalNote;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalNoteQueryRepository extends JpaRepository<ClinicalNote, UUID> {

  Optional<ClinicalNote> findFirstByEncounterIdOrderByNoteVersionDesc(UUID encounterId);

  List<ClinicalNote> findByEncounterIdOrderByNoteVersionDesc(UUID encounterId);
}
