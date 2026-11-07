package com.mediflow.shared.repo;

import com.mediflow.shared.domain.SystemSetting;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, UUID> {
}
