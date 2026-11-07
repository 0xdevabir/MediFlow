package com.mediflow.shared.repo;

import com.mediflow.shared.domain.BreakGlassAccess;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreakGlassAccessRepository extends JpaRepository<BreakGlassAccess, UUID> {
}
