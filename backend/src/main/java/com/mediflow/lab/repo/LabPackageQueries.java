package com.mediflow.lab.repo;

import com.mediflow.shared.domain.LabPackage;
import com.mediflow.shared.domain.LabPackageItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Read queries on the shared {@code lab_package} / {@code lab_package_item} tables. */
public interface LabPackageQueries extends Repository<LabPackage, UUID> {

  @Query(
      "select p from LabPackage p where p.active = true"
          + " and (p.hospitalId is null or p.hospitalId = :hospitalId) order by p.name")
  List<LabPackage> findActiveForHospital(UUID hospitalId);

  @Query("select p from LabPackage p where p.active = true order by p.name")
  List<LabPackage> findAllActive();

  @Query("select i from LabPackageItem i where i.packageId = :packageId")
  List<LabPackageItem> items(UUID packageId);
}
