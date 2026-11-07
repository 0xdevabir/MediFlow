package com.mediflow.prescription.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Incident record opened on revoke (DOCX §12.1): reviewed within 24 h. */
@Entity
@Table(name = "rx_incident")
@Getter
@Setter
public class RxIncident extends BaseEntity {

  @Column(name = "prescription_id", nullable = false)
  private UUID prescriptionId;

  @Column(name = "opened_by", nullable = false)
  private UUID openedBy;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  @Column(name = "review_due_at", nullable = false)
  private Instant reviewDueAt;

  @Column(name = "dispensed_before_revoke", nullable = false)
  private boolean dispensedBeforeRevoke;

  @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
  private String reason;

  @Column(name = "status", nullable = false)
  private String status = "OPEN";
}
