package com.mediflow.clinical.vitals.repo;

import com.mediflow.shared.domain.VitalRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalVitalRuleRepository extends JpaRepository<VitalRule, UUID> {

  List<VitalRule> findByActiveTrue();
}
