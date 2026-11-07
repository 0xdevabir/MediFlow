package com.mediflow.clinical.vitals.repo;

import com.mediflow.clinical.vitals.domain.VitalDefinitionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VitalDefinitionRepository extends JpaRepository<VitalDefinitionEntity, UUID> {

  List<VitalDefinitionEntity> findByActiveTrueOrderBySortOrderAsc();

  List<VitalDefinitionEntity> findByActiveTrueAndNurseEntryTrueOrderBySortOrderAsc();

  Optional<VitalDefinitionEntity> findByCodeAndActiveTrue(String code);
}
