package com.mediflow.notification.repo;

import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only recipient resolution over T00 tables (users, practitioners, patients). */
public interface RecipientLookupRepository extends Repository<AppUser, UUID> {

  @Query("select distinct u.id from AppUser u join u.roles r where r = :role and u.enabled = true")
  List<UUID> findEnabledUserIdsByRole(@Param("role") Role role);

  /** Users of practitioners with an active role at the hospital and the given system role. */
  @Query(
      "select distinct u.id from AppUser u join u.roles r, Practitioner p, PractitionerRoleEntity pr"
          + " where p.userId = u.id and pr.practitionerId = p.id and pr.hospitalId = :hospitalId"
          + " and pr.active = true and r = :role and u.enabled = true")
  List<UUID> findUserIdsAtHospital(
      @Param("hospitalId") UUID hospitalId, @Param("role") Role role);

  @Query("select p.userId from Practitioner p where p.id = :practitionerId")
  List<UUID> findPractitionerUserId(@Param("practitionerId") UUID practitionerId);

  @Query("select p.id from Patient p where p.userId = :userId")
  List<UUID> findPatientIdByUserId(@Param("userId") UUID userId);

  @Query("select q.tokenNo from QueueEntry q where q.appointmentId = :appointmentId")
  List<String> findQueueToken(@Param("appointmentId") UUID appointmentId);

  @Query("select s.startsAt from Appointment a, Slot s where a.id = :appointmentId and s.id = a.slotId")
  List<java.time.Instant> findAppointmentStart(@Param("appointmentId") UUID appointmentId);

  @Query("select a.doctorId from Appointment a where a.id = :appointmentId")
  List<UUID> findAppointmentDoctor(@Param("appointmentId") UUID appointmentId);
}
