package com.mediflow.shared.repo;

import com.mediflow.shared.domain.Prescription;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {
}
