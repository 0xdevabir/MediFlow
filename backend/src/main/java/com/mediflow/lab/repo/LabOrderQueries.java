package com.mediflow.lab.repo;

import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.OrderType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Read queries on the shared {@code lab_order} table that the lab module needs. */
public interface LabOrderQueries extends Repository<LabOrder, UUID> {

  List<LabOrder> findByPatientIdOrderByOrderedAtDesc(UUID patientId);

  List<LabOrder> findByStatusInOrderByOrderedAtAsc(Collection<LabOrderStatus> statuses);

  List<LabOrder> findByOrderedByAndOrderTypeOrderByOrderedAtDesc(UUID orderedBy, OrderType type);

  List<LabOrder> findByEncounterIdOrderByOrderedAtAsc(UUID encounterId);
}
