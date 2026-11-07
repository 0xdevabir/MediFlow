package com.mediflow.shared.repo;

import com.mediflow.shared.domain.LabPackageItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabPackageItemRepository extends JpaRepository<LabPackageItem, LabPackageItem.Pk> {
}
