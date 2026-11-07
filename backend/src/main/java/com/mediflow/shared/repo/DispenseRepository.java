package com.mediflow.shared.repo;

import com.mediflow.shared.domain.Dispense;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispenseRepository extends JpaRepository<Dispense, UUID> {
}
