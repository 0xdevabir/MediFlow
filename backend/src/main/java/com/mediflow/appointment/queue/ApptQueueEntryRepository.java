package com.mediflow.appointment.queue;

import com.mediflow.shared.domain.QueueEntry;
import com.mediflow.shared.domain.QueueStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApptQueueEntryRepository extends JpaRepository<QueueEntry, UUID> {

  Optional<QueueEntry> findByAppointmentId(UUID appointmentId);

  List<QueueEntry> findByIdIn(Collection<UUID> ids);

  List<QueueEntry> findByHospitalIdAndStatusIn(UUID hospitalId, Collection<QueueStatus> statuses);
}
