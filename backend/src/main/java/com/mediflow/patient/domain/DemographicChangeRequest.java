package com.mediflow.patient.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "patient_demographic_change")
@Getter
@Setter
public class DemographicChangeRequest extends BaseEntity {

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "requested_by")
  private UUID requestedBy;

  @Column(name = "new_given_name")
  private String newGivenName;

  @Column(name = "new_family_name")
  private String newFamilyName;

  @Column(name = "new_date_of_birth")
  private LocalDate newDateOfBirth;

  @Column(name = "reason", columnDefinition = "TEXT")
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ChangeRequestStatus status = ChangeRequestStatus.PENDING;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "review_note", columnDefinition = "TEXT")
  private String reviewNote;
}
