package com.mediflow.shared.repo;

import com.mediflow.shared.domain.AiFlagOverride;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiFlagOverrideRepository extends JpaRepository<AiFlagOverride, UUID> {
}
