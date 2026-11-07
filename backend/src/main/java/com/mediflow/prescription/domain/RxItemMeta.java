package com.mediflow.prescription.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** T06-owned extension of {@code prescription_item}: gate status, per-field confidence, AI guess. */
@Entity
@Table(name = "rx_item_meta")
@Getter
@Setter
public class RxItemMeta extends BaseEntity {

  @Column(name = "item_id", nullable = false, unique = true)
  private UUID itemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false)
  private ItemSource source;

  @Enumerated(EnumType.STRING)
  @Column(name = "gate_status", nullable = false)
  private GateStatus gateStatus;

  /** Doctor explicitly confirmed/corrected this item (required for NEEDS_REVIEW / UNKNOWN). */
  @Column(name = "resolved", nullable = false)
  private boolean resolved;

  @Column(name = "resolved_by")
  private UUID resolvedBy;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "conf_drug")
  private BigDecimal confDrug;

  @Column(name = "conf_dose")
  private BigDecimal confDose;

  @Column(name = "conf_frequency")
  private BigDecimal confFrequency;

  @Column(name = "conf_route")
  private BigDecimal confRoute;

  @Column(name = "conf_duration")
  private BigDecimal confDuration;

  @Column(name = "ai_job_id")
  private UUID aiJobId;

  @Column(name = "ai_brand")
  private String aiBrand;

  @Column(name = "ai_generic")
  private String aiGeneric;

  @Column(name = "ai_strength")
  private String aiStrength;

  @Column(name = "ai_dose")
  private String aiDose;

  @Column(name = "ai_route")
  private String aiRoute;

  @Column(name = "ai_frequency")
  private String aiFrequency;

  @Column(name = "ai_duration")
  private String aiDuration;

  @Column(name = "ai_generic_id")
  private UUID aiGenericId;

  /** ACCEPTED / CORRECTED, set at signing for AI items (KPI "useful AI assistance"). */
  @Column(name = "ai_outcome")
  private String aiOutcome;

  @Column(name = "position", nullable = false)
  private int position;
}
