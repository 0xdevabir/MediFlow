package com.mediflow.patient.repo;

import com.mediflow.patient.domain.PatientDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientDocumentRepository extends JpaRepository<PatientDocument, UUID> {

  List<PatientDocument> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
