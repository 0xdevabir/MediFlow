package com.mediflow.prescription.repo;

import com.mediflow.prescription.domain.RxPrescriptionMeta;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RxPrescriptionMetaRepository extends JpaRepository<RxPrescriptionMeta, UUID> {
  Optional<RxPrescriptionMeta> findByPrescriptionId(UUID prescriptionId);

  List<RxPrescriptionMeta> findByAmendsPrescriptionId(UUID prescriptionId);

  List<RxPrescriptionMeta> findByDoctorUserIdOrderByUpdatedAtDesc(UUID doctorUserId);
}
