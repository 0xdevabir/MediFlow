package com.mediflow.patient.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Patient-module view of the shared {@code allergy} table including the verification columns
 * added in V150. The shared {@code Allergy} entity maps the same table without those columns, so
 * rows it inserts default to unverified ("unknown is never safe").
 */
@Entity(name = "PatientAllergy")
@Table(name = "allergy")
@Getter
@Setter
public class PatientAllergy extends BaseEntity {

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "substance", nullable = false)
  private String substance;

  @Column(name = "substance_code")
  private String substanceCode;

  @Column(name = "criticality")
  private String criticality;

  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  @Column(name = "verified", nullable = false)
  private boolean verified;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false)
  private ClinicalSource source = ClinicalSource.SELF_REPORTED;

  @Column(name = "reported_by")
  private UUID reportedBy;

  @Column(name = "verified_by")
  private UUID verifiedBy;

  @Column(name = "verified_at")
  private Instant verifiedAt;
}
