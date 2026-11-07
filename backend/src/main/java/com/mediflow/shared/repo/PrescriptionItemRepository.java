package com.mediflow.shared.repo;

import com.mediflow.shared.domain.PrescriptionItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, UUID> {
}
