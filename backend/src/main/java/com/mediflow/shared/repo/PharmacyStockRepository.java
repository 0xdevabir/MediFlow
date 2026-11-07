package com.mediflow.shared.repo;

import com.mediflow.shared.domain.PharmacyStock;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyStockRepository extends JpaRepository<PharmacyStock, UUID> {
}
