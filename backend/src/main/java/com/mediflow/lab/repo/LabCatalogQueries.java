package com.mediflow.lab.repo;

import com.mediflow.shared.domain.LabTest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Read queries on the shared {@code lab_test} table. */
public interface LabCatalogQueries extends Repository<LabTest, UUID> {

  List<LabTest> findByActiveTrueOrderByNameAsc();

  List<LabTest> findByActiveTrueAndDirectAllowedTrueOrderByNameAsc();
}
