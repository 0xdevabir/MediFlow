package com.mediflow.appointment.triage;

import com.mediflow.shared.domain.Triage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApptTriageRepository extends JpaRepository<Triage, UUID> {

  Optional<Triage> findByAppointmentId(UUID appointmentId);
}
