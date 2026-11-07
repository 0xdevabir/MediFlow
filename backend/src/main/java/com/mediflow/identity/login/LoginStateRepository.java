package com.mediflow.identity.login;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginStateRepository extends JpaRepository<LoginState, UUID> {

  /** Row lock so concurrent failed logins cannot under-count. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from LoginState s where s.userId = :userId")
  Optional<LoginState> findForUpdate(@Param("userId") UUID userId);
}
