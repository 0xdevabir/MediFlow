package com.mediflow.appointment.support;

import com.mediflow.shared.domain.Practitioner;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ApptPractitionerRepository extends Repository<Practitioner, UUID> {

  Optional<Practitioner> findById(UUID id);

  Optional<Practitioner> findFirstByUserId(UUID userId);
}
