package com.mediflow.appointment.queue;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueMetaRepository extends JpaRepository<QueueMeta, UUID> {

  Optional<QueueMeta> findByAppointmentId(UUID appointmentId);

  Optional<QueueMeta> findByQueueEntryId(UUID queueEntryId);

  List<QueueMeta> findByDoctorIdAndQueueDate(UUID doctorId, LocalDate queueDate);

  List<QueueMeta> findByDoctorIdAndQueueDateAndAppointmentIdIn(
      UUID doctorId, LocalDate queueDate, Collection<UUID> appointmentIds);
}
