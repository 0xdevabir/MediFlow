package com.mediflow.prescription.repo;

import com.mediflow.shared.domain.PrescriptionItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** T06 queries over the shared {@code prescription_item} table. */
public interface RxItemQueries extends JpaRepository<PrescriptionItem, UUID> {
  List<PrescriptionItem> findByPrescriptionIdOrderByCreatedAtAsc(UUID prescriptionId);

  List<PrescriptionItem> findByPrescriptionIdIn(Collection<UUID> prescriptionIds);
}
