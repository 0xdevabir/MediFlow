package com.mediflow.shared.repo;

import com.mediflow.shared.domain.Slot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotRepository extends JpaRepository<Slot, UUID> {
}
