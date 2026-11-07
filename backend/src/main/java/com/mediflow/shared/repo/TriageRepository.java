package com.mediflow.shared.repo;

import com.mediflow.shared.domain.Triage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriageRepository extends JpaRepository<Triage, UUID> {
}
