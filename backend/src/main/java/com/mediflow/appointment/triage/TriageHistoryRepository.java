package com.mediflow.appointment.triage;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriageHistoryRepository extends JpaRepository<TriageHistory, UUID> {

  List<TriageHistory> findByAppointmentIdOrderBySetAtAsc(UUID appointmentId);
}
