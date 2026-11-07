package com.mediflow.appointment.redalert;

import com.mediflow.shared.domain.RedAlert;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApptRedAlertRepository extends JpaRepository<RedAlert, UUID> {

  @Query(
      "select r from RedAlert r where r.hospitalId = :hospitalId and r.handoverStatus is null"
          + " order by r.raisedAt desc")
  List<RedAlert> findActiveByHospital(@Param("hospitalId") UUID hospitalId);

  @Query("select r from RedAlert r where r.handoverStatus is null order by r.raisedAt desc")
  List<RedAlert> findAllActive();

  Optional<RedAlert> findFirstByAppointmentIdAndHandoverStatusIsNull(UUID appointmentId);

  Optional<RedAlert> findFirstByPatientIdAndHandoverStatusIsNull(UUID patientId);

  @Modifying(clearAutomatically = true)
  @Query(
      value =
          "update red_alert set"
              + " assigned_er_user_id = coalesce(:erUserId, assigned_er_user_id),"
              + " source = coalesce(:source, source),"
              + " acknowledged_by = coalesce(:ackBy, acknowledged_by),"
              + " handover_by = coalesce(:handoverBy, handover_by),"
              + " handover_at = coalesce(:handoverAt, handover_at),"
              + " handover_note = coalesce(:handoverNote, handover_note),"
              + " updated_at = now() where id = :id",
      nativeQuery = true)
  int updateExt(
      @Param("id") UUID id,
      @Param("erUserId") UUID erUserId,
      @Param("source") String source,
      @Param("ackBy") UUID ackBy,
      @Param("handoverBy") UUID handoverBy,
      @Param("handoverAt") java.time.Instant handoverAt,
      @Param("handoverNote") String handoverNote);

  @Query(
      value =
          "select assigned_er_user_id, acknowledged_by, source, handover_by, handover_at,"
              + " handover_note from red_alert where id = :id",
      nativeQuery = true)
  List<Object[]> loadExt(@Param("id") UUID id);
}
