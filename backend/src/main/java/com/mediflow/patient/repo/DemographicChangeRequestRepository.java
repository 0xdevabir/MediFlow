package com.mediflow.patient.repo;

import com.mediflow.patient.domain.ChangeRequestStatus;
import com.mediflow.patient.domain.DemographicChangeRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemographicChangeRequestRepository
    extends JpaRepository<DemographicChangeRequest, UUID> {

  List<DemographicChangeRequest> findByStatusOrderByCreatedAtAsc(ChangeRequestStatus status);

  List<DemographicChangeRequest> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

  boolean existsByPatientIdAndStatus(UUID patientId, ChangeRequestStatus status);
}
