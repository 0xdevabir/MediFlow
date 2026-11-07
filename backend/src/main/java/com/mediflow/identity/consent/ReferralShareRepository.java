package com.mediflow.identity.consent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralShareRepository extends JpaRepository<ReferralShare, UUID> {

  List<ReferralShare> findByPatientIdOrderByStartsAtDesc(UUID patientId);
}
