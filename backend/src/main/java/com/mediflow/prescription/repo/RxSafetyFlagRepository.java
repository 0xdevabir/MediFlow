package com.mediflow.prescription.repo;

import com.mediflow.prescription.domain.RxSafetyFlag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RxSafetyFlagRepository extends JpaRepository<RxSafetyFlag, UUID> {
  List<RxSafetyFlag> findByPrescriptionId(UUID prescriptionId);

  @Modifying(flushAutomatically = true, clearAutomatically = false)
  @Query("delete from RxSafetyFlag f where f.prescriptionId = :prescriptionId")
  void deleteAllForPrescription(@Param("prescriptionId") UUID prescriptionId);
}
