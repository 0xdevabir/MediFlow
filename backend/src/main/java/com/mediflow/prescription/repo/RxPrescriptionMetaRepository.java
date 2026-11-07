package com.mediflow.prescription.repo;

import com.mediflow.prescription.domain.RxPrescriptionMeta;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RxPrescriptionMetaRepository extends JpaRepository<RxPrescriptionMeta, UUID> {
  Optional<RxPrescriptionMeta> findByPrescriptionId(UUID prescriptionId);

  java.util.List<RxPrescriptionMeta> findByAmendsPrescriptionId(UUID prescriptionId);
}
