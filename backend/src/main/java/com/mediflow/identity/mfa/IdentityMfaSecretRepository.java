package com.mediflow.identity.mfa;

import com.mediflow.shared.domain.MfaSecret;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Identity-owned finder over the shared {@code mfa_secret} table. */
public interface IdentityMfaSecretRepository extends JpaRepository<MfaSecret, UUID> {

  Optional<MfaSecret> findByUserId(UUID userId);

  void deleteByUserId(UUID userId);
}
