package com.mediflow.shared.repo;

import com.mediflow.shared.domain.Department;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
}
