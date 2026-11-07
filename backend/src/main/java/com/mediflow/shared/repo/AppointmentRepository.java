package com.mediflow.shared.repo;

import com.mediflow.shared.domain.Appointment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
}
