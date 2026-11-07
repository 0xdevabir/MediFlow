package com.mediflow.identity.breakglass;

import com.mediflow.shared.domain.BreakGlassAccess;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Identity-owned finders over the shared {@code break_glass_access} table. */
public interface IdentityBreakGlassRepository extends JpaRepository<BreakGlassAccess, UUID> {

  @Query(
      "select b from BreakGlassAccess b where b.userId = :userId and b.patientId = :patientId"
          + " and b.startedAt <= :now and b.expiresAt > :now order by b.expiresAt desc")
  List<BreakGlassAccess> findActive(
      @Param("userId") UUID userId, @Param("patientId") UUID patientId, @Param("now") Instant now);

  List<BreakGlassAccess> findTop200ByOrderByStartedAtDesc();
}
