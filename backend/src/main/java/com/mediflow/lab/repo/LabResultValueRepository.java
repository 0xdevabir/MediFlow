package com.mediflow.lab.repo;

import com.mediflow.lab.domain.LabResultValue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LabResultValueRepository extends JpaRepository<LabResultValue, UUID> {

  List<LabResultValue> findByReportIdOrderBySortOrderAsc(UUID reportId);

  @Modifying(flushAutomatically = true, clearAutomatically = false)
  @Query("delete from LabResultValue v where v.reportId = :reportId")
  void deleteByReport(UUID reportId);
}
