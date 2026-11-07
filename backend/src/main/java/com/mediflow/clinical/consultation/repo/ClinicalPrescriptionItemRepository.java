package com.mediflow.clinical.consultation.repo;

import com.mediflow.shared.domain.PrescriptionItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalPrescriptionItemRepository extends JpaRepository<PrescriptionItem, UUID> {

  List<PrescriptionItem> findByPrescriptionIdIn(Collection<UUID> prescriptionIds);
}
