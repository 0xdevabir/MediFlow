package com.mediflow.shared.repo;

import com.mediflow.shared.domain.DrugBrand;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrugBrandRepository extends JpaRepository<DrugBrand, UUID> {
}
