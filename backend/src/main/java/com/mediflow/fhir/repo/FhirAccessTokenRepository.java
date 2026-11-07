package com.mediflow.fhir.repo;

import com.mediflow.fhir.domain.FhirAccessToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FhirAccessTokenRepository extends JpaRepository<FhirAccessToken, UUID> {

  Optional<FhirAccessToken> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
}
