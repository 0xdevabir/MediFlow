package com.mediflow.patient.domain;

import com.mediflow.shared.domain.BaseEntity;
import com.mediflow.shared.encryption.AesGcmAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "patient_history_item")
@Getter
@Setter
public class PatientHistoryItem extends BaseEntity {

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private HistoryCategory category;

  @Convert(converter = AesGcmAttributeConverter.class)
  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "since_text")
  private String sinceText;

  @Column(name = "verified", nullable = false)
  private boolean verified;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false)
  private ClinicalSource source;

  @Column(name = "reported_by")
  private UUID reportedBy;

  @Column(name = "verified_by")
  private UUID verifiedBy;

  @Column(name = "verified_at")
  private Instant verifiedAt;
}
