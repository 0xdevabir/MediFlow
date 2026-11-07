package com.mediflow.shared.repo;

import com.mediflow.shared.domain.Consent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {
}
