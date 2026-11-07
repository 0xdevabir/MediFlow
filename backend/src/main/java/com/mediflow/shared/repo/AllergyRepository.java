package com.mediflow.shared.repo;

import com.mediflow.shared.domain.Allergy;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllergyRepository extends JpaRepository<Allergy, UUID> {
}
