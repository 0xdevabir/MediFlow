package com.mediflow.identity.breakglass;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreakGlassReviewRepository extends JpaRepository<BreakGlassReview, UUID> {

  Optional<BreakGlassReview> findByBreakGlassId(UUID breakGlassId);
}
