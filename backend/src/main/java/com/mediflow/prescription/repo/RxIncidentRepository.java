package com.mediflow.prescription.repo;

import com.mediflow.prescription.domain.RxIncident;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RxIncidentRepository extends JpaRepository<RxIncident, UUID> {
  List<RxIncident> findByPrescriptionId(UUID prescriptionId);

  List<RxIncident> findByStatusOrderByReviewDueAtAsc(String status);
}
