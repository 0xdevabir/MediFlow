package com.mediflow.lab.repo;

import com.mediflow.shared.domain.LabTest;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Read queries on the shared {@code lab_test} table. */
public interface LabCatalogQueries extends Repository<LabTest, UUID> {

  Optional<LabTest> findById(UUID id);

  List<LabTest> findByIdIn(Collection<UUID> ids);

  List<LabTest> findByActiveTrueOrderByNameAsc();

  List<LabTest> findByActiveTrueAndDirectAllowedTrueOrderByNameAsc();
}

