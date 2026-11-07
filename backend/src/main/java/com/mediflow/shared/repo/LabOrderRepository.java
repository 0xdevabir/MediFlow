package com.mediflow.shared.repo;

import com.mediflow.shared.domain.LabOrder;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabOrderRepository extends JpaRepository<LabOrder, UUID> {
}
