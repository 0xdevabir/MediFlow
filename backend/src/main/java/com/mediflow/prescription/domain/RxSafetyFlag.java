package com.mediflow.prescription.domain;

import com.mediflow.shared.domain.BaseEntity;
import com.mediflow.shared.domain.ConflictSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Latest contradiction-engine findings for a draft (replaced on every draft change). */
@Entity
@Table(name = "rx_safety_flag")
@Getter
@Setter
public class RxSafetyFlag extends BaseEntity {

  @Column(name = "prescription_id", nullable = false)
  private UUID prescriptionId;

  @Column(name = "item_id")
  private UUID itemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "check_type", nullable = false)
  private CheckType checkType;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", nullable = false)
  private ConflictSeverity severity;

  @Column(name = "fingerprint", nullable = false)
  private String fingerprint;

  @Column(name = "message_key", nullable = false)
  private String messageKey;

  @Column(name = "detail", columnDefinition = "TEXT")
  private String detail;

  @Column(name = "overridable", nullable = false)
  private boolean overridable;
}
