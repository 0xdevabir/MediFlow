package com.mediflow.prescription.repo;

import com.mediflow.prescription.domain.RxFlagAck;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RxFlagAckRepository extends JpaRepository<RxFlagAck, UUID> {
  List<RxFlagAck> findByPrescriptionId(UUID prescriptionId);

  List<RxFlagAck> findByItemId(UUID itemId);

  Optional<RxFlagAck> findByItemIdAndFingerprint(UUID itemId, String fingerprint);
}
