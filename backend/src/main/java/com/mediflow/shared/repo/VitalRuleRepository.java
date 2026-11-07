package com.mediflow.shared.repo;

import com.mediflow.shared.domain.VitalRule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VitalRuleRepository extends JpaRepository<VitalRule, UUID> {
}
