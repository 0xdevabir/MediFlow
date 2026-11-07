package com.mediflow.shared.repo;

import com.mediflow.shared.domain.DrugInteraction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrugInteractionRepository extends JpaRepository<DrugInteraction, UUID> {
}
