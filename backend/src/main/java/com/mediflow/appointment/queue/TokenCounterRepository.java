package com.mediflow.appointment.queue;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TokenCounterRepository extends JpaRepository<TokenCounter, TokenCounter.Pk> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select t from TokenCounter t where t.doctorId = :doctorId and t.queueDate = :queueDate")
  Optional<TokenCounter> findForUpdate(
      @Param("doctorId") UUID doctorId, @Param("queueDate") LocalDate queueDate);
}
