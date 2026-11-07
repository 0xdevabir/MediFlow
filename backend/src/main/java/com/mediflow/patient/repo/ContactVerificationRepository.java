package com.mediflow.patient.repo;

import com.mediflow.patient.domain.ContactChannel;
import com.mediflow.patient.domain.ContactVerification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactVerificationRepository extends JpaRepository<ContactVerification, UUID> {

  Optional<ContactVerification> findFirstByPatientIdAndChannelAndConsumedAtIsNullOrderByCreatedAtDesc(
      UUID patientId, ContactChannel channel);

  List<ContactVerification> findByPatientIdAndChannelAndConsumedAtIsNull(
      UUID patientId, ContactChannel channel);
}
