package com.mediflow.shared.repo;

import com.mediflow.shared.domain.DiagnosticReport;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosticReportRepository extends JpaRepository<DiagnosticReport, UUID> {
}
