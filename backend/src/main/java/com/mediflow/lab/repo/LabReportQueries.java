package com.mediflow.lab.repo;

import com.mediflow.shared.domain.DiagnosticReport;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Diagnostic-report lookups for the lab workflow. */
public interface LabReportQueries extends Repository<DiagnosticReport, UUID> {

  Optional<DiagnosticReport> findById(UUID id);

  Optional<DiagnosticReport> findByLabOrderId(UUID labOrderId);

  List<DiagnosticReport> findByLabOrderIdIn(Collection<UUID> labOrderIds);
}
