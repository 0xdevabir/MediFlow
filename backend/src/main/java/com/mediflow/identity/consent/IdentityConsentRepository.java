package com.mediflow.identity.consent;

import com.mediflow.shared.domain.Consent;
import com.mediflow.shared.domain.ConsentType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Identity-owned finders over the shared {@code consent} table. */
public interface IdentityConsentRepository extends JpaRepository<Consent, UUID> {

  List<Consent> findByPatientIdAndConsentTypeOrderByGrantedAtDesc(UUID patientId, ConsentType type);

  List<Consent> findByPatientIdOrderByGrantedAtDesc(UUID patientId);
}
