package com.mediflow.lab.repo;

import com.mediflow.lab.domain.LabOrderDetail;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabOrderDetailRepository extends JpaRepository<LabOrderDetail, UUID> {

  Optional<LabOrderDetail> findByLabOrderId(UUID labOrderId);

  List<LabOrderDetail> findByLabOrderIdIn(Collection<UUID> labOrderIds);
}
