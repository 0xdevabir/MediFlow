package com.mediflow.notification.repo;

import com.mediflow.notification.domain.PushDeviceToken;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, UUID> {

  List<PushDeviceToken> findByUserId(UUID userId);

  boolean existsByUserIdAndToken(UUID userId, String token);

  void deleteByUserIdAndToken(UUID userId, String token);
}
