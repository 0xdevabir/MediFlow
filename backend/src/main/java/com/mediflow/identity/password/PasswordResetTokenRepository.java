package com.mediflow.identity.password;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /** Invalidates every outstanding token of the user (a new request or a successful reset). */
  @Modifying
  @Query(
      "update PasswordResetToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
  int invalidateOutstanding(@Param("userId") UUID userId, @Param("now") Instant now);
}
