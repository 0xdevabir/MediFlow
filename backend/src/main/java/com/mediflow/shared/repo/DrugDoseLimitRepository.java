package com.mediflow.shared.repo;

import com.mediflow.shared.domain.DrugDoseLimit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrugDoseLimitRepository extends JpaRepository<DrugDoseLimit, UUID> {
}
