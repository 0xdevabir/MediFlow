package com.mediflow.clinical.consultation.repo;

import com.mediflow.clinical.consultation.domain.LabBadgeEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabBadgeRepository extends JpaRepository<LabBadgeEntity, UUID> {

  long countByDoctorUserIdAndReadAtIsNull(UUID doctorUserId);

  List<LabBadgeEntity> findByDoctorUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID doctorUserId);

  List<LabBadgeEntity> findByDoctorUserIdAndPatientIdAndReadAtIsNull(
      UUID doctorUserId, UUID patientId);

  Optional<LabBadgeEntity> findByDoctorUserIdAndReportId(UUID doctorUserId, UUID reportId);
}
