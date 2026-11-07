package com.mediflow.notification.repo;

import com.mediflow.shared.domain.PrescriptionItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only: signed prescription items + display names for reminder generation. */
public interface PrescriptionLookupRepository extends Repository<PrescriptionItem, UUID> {

  List<PrescriptionItem> findByPrescriptionId(UUID prescriptionId);

  @Query("select g.name from DrugGeneric g where g.id = :id")
  List<String> findGenericName(@Param("id") UUID id);

  @Query("select b.name from DrugBrand b where b.id = :id")
  List<String> findBrandName(@Param("id") UUID id);
}
