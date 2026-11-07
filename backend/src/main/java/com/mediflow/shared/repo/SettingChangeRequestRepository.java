package com.mediflow.shared.repo;

import com.mediflow.shared.domain.SettingChangeRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingChangeRequestRepository extends JpaRepository<SettingChangeRequest, UUID> {
}
