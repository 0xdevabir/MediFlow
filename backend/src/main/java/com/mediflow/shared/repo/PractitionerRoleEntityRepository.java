package com.mediflow.shared.repo;

import com.mediflow.shared.domain.PractitionerRoleEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PractitionerRoleEntityRepository extends JpaRepository<PractitionerRoleEntity, UUID> {
}
