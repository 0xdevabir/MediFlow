package com.mediflow.shared.repo;

import com.mediflow.shared.domain.LabTest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabTestRepository extends JpaRepository<LabTest, UUID> {
}
