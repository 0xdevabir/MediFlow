package com.mediflow.shared.repo;

import com.mediflow.shared.domain.ClinicalNote;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, UUID> {
}
