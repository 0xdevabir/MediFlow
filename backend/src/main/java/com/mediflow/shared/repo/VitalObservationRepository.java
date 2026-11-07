package com.mediflow.shared.repo;

import com.mediflow.shared.domain.VitalObservation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VitalObservationRepository extends JpaRepository<VitalObservation, UUID> {
}
