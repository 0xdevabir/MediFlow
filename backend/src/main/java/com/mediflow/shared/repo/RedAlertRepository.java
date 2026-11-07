package com.mediflow.shared.repo;

import com.mediflow.shared.domain.RedAlert;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RedAlertRepository extends JpaRepository<RedAlert, UUID> {
}
