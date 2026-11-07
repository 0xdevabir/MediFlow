package com.mediflow.shared.repo;

import com.mediflow.shared.domain.Hospital;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HospitalRepository extends JpaRepository<Hospital, UUID> {
}
