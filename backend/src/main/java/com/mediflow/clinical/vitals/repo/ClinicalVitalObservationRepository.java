package com.mediflow.clinical.vitals.repo;

import com.mediflow.shared.domain.VitalObservation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalVitalObservationRepository extends JpaRepository<VitalObservation, UUID> {

  List<VitalObservation> findByEncounterIdOrderByRecordedAtAsc(UUID encounterId);

  List<VitalObservation> findByPatientIdAndVitalTypeAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
      UUID patientId, String vitalType, Instant since);

  List<VitalObservation> findByPatientIdOrderByRecordedAtDesc(UUID patientId);
}
