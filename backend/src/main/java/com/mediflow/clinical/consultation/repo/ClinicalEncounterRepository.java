package com.mediflow.clinical.consultation.repo;

import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.EncounterStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalEncounterRepository extends JpaRepository<Encounter, UUID> {

  List<Encounter> findByPatientIdAndStatus(UUID patientId, EncounterStatus status);

  List<Encounter> findByDoctorIdAndStatusOrderByStartedAtDesc(UUID doctorId, EncounterStatus status);

  Optional<Encounter> findByAppointmentId(UUID appointmentId);
}
