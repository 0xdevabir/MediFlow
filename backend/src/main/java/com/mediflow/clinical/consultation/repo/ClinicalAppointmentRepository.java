package com.mediflow.clinical.consultation.repo;

import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AppointmentStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalAppointmentRepository extends JpaRepository<Appointment, UUID> {

  List<Appointment> findByPatientIdAndStatusIn(UUID patientId, Collection<AppointmentStatus> statuses);

  List<Appointment> findByDoctorIdAndStatusInOrderByBookedAtDesc(
      UUID doctorId, Collection<AppointmentStatus> statuses);
}
