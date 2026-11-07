package com.mediflow.fhir.repo;

import com.mediflow.fhir.domain.FhirOAuthClient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FhirOAuthClientRepository extends JpaRepository<FhirOAuthClient, UUID> {

  Optional<FhirOAuthClient> findByClientIdAndActiveTrue(String clientId);
}
