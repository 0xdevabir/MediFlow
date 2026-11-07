package com.mediflow.shared.repo;

import com.mediflow.shared.domain.MfaSecret;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaSecretRepository extends JpaRepository<MfaSecret, UUID> {
}
