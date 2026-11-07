package com.mediflow.clinical.consultation.repo;

import com.mediflow.shared.domain.DiagnosticReport;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalDiagnosticReportRepository extends JpaRepository<DiagnosticReport, UUID> {

  List<DiagnosticReport> findByLabOrderIdIn(Collection<UUID> labOrderIds);

  Optional<DiagnosticReport> findByLabOrderId(UUID labOrderId);
}
