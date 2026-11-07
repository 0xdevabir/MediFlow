package com.mediflow.prescription.repo;

import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** T06 queries over the shared {@code prescription} table. */
public interface RxPrescriptionQueries extends JpaRepository<Prescription, UUID> {
  List<Prescription> findByEncounterIdOrderByCreatedAtDesc(UUID encounterId);

  List<Prescription> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

  List<Prescription> findByPatientIdAndStatusAndSignedAtAfter(
      UUID patientId, PrescriptionStatus status, Instant after);
}
