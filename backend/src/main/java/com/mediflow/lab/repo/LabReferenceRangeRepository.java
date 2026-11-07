package com.mediflow.lab.repo;

import com.mediflow.lab.domain.LabReferenceRange;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabReferenceRangeRepository extends JpaRepository<LabReferenceRange, UUID> {

  List<LabReferenceRange> findByTestIdInOrderBySortOrderAsc(Collection<UUID> testIds);
}
