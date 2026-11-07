package com.mediflow.shared.repo;

import com.mediflow.shared.domain.Practitioner;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PractitionerRepository extends JpaRepository<Practitioner, UUID> {
}
