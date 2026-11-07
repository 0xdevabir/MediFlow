package com.mediflow.patient.repo;

import com.mediflow.patient.domain.PatientHistoryItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientHistoryItemRepository extends JpaRepository<PatientHistoryItem, UUID> {

  List<PatientHistoryItem> findByPatientIdOrderByCreatedAtAsc(UUID patientId);
}
