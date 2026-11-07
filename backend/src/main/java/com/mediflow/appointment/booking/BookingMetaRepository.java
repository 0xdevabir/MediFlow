package com.mediflow.appointment.booking;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingMetaRepository extends JpaRepository<BookingMeta, UUID> {

  Optional<BookingMeta> findByAppointmentId(UUID appointmentId);

  Optional<BookingMeta> findByCheckinCode(String checkinCode);

  List<BookingMeta> findByAppointmentIdIn(Collection<UUID> appointmentIds);
}
