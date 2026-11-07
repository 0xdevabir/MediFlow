package com.mediflow.shared.repo;

import com.mediflow.shared.domain.DrugGeneric;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrugGenericRepository extends JpaRepository<DrugGeneric, UUID> {
}
