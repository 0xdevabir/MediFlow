package com.mediflow.appointment.support;

import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AppointmentStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** M2's own query surface over the shared {@code appointment} table. */
public interface ApptAppointmentRepository extends JpaRepository<Appointment, UUID> {

  List<Appointment> findByPatientIdOrderByBookedAtDesc(UUID patientId);

  boolean existsByPatientIdAndSlotIdAndStatusNot(
      UUID patientId, UUID slotId, AppointmentStatus status);

  List<Appointment> findByHospitalIdAndStatusIn(UUID hospitalId, Collection<AppointmentStatus> s);

  List<Appointment> findByStatusInOrderByBookedAtAsc(Collection<AppointmentStatus> statuses);
}
