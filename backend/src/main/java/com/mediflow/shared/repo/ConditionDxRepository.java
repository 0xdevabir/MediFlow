package com.mediflow.shared.repo;

import com.mediflow.shared.domain.ConditionDx;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConditionDxRepository extends JpaRepository<ConditionDx, UUID> {
}
