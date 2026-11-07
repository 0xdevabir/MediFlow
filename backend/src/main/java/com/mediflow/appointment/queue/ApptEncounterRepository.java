package com.mediflow.appointment.queue;

import com.mediflow.shared.domain.Encounter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ApptEncounterRepository extends Repository<Encounter, UUID> {

  Encounter save(Encounter encounter);

  Optional<Encounter> findByAppointmentId(UUID appointmentId);

  Optional<Encounter> findById(UUID id);
}
