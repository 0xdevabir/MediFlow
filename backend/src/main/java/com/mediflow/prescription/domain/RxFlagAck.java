package com.mediflow.prescription.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Doctor's override/acknowledgement of one finding, keyed by (item, fingerprint) so it survives
 * re-evaluation but lapses when the item changes (new fingerprint). Reason lives in {@code
 * ai_flag_override}.
 */
@Entity
@Table(name = "rx_flag_ack")
@Getter
@Setter
public class RxFlagAck extends BaseEntity {

  @Column(name = "prescription_id", nullable = false)
  private UUID prescriptionId;

  @Column(name = "item_id", nullable = false)
  private UUID itemId;

  @Column(name = "fingerprint", nullable = false)
  private String fingerprint;

  @Column(name = "override_id", nullable = false)
  private UUID overrideId;

  @Column(name = "second_confirmed", nullable = false)
  private boolean secondConfirmed;

  @Column(name = "acked_by", nullable = false)
  private UUID ackedBy;

  @Column(name = "acked_at", nullable = false)
  private Instant ackedAt;
}
