package com.mediflow.prescription.repo;

import com.mediflow.shared.domain.Allergy;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.ConditionDx;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.domain.VitalObservation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only patient context for the safety engine and signing (shared tables, T06 queries). */
public interface RxClinicalQueries extends Repository<Allergy, UUID> {

  @Query("select a from Allergy a where a.patientId = :patientId")
  List<Allergy> allergies(@Param("patientId") UUID patientId);

  @Query("select c from ConditionDx c where c.patientId = :patientId")
  List<ConditionDx> conditions(@Param("patientId") UUID patientId);

  @Query(
      "select v from VitalObservation v where v.patientId = :patientId order by v.recordedAt desc")
  List<VitalObservation> vitalsNewestFirst(@Param("patientId") UUID patientId);

  @Query("select p from Practitioner p where p.userId = :userId")
  List<Practitioner> practitionersOfUser(@Param("userId") UUID userId);

  default Optional<Practitioner> practitionerOfUser(UUID userId) {
    return practitionersOfUser(userId).stream().findFirst();
  }

  @Query("select u from AppUser u join u.roles r where r = :role and u.enabled = true")
  List<AppUser> usersWithRole(@Param("role") Role role);

  @Query("select count(d) from Dispense d where d.prescriptionId = :prescriptionId")
  long dispenseCount(@Param("prescriptionId") UUID prescriptionId);
}
