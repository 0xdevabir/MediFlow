package com.mediflow.lab.repo;

import com.mediflow.shared.domain.Sample;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Sample lookups for the lab workflow. */
public interface LabSampleQueries extends Repository<Sample, UUID> {

  Optional<Sample> findByLabOrderId(UUID labOrderId);

  List<Sample> findByLabOrderIdIn(Collection<UUID> labOrderIds);
}
