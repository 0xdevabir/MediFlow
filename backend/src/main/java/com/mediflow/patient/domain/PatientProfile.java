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

/** Patient-module metadata beside the shared {@code patient} row (1:1). */
@Entity
@Table(name = "patient_profile")
@Getter
@Setter
public class PatientProfile extends BaseEntity {

  @Column(name = "patient_id", nullable = false, unique = true)
  private UUID patientId;

  @Enumerated(EnumType.STRING)
  @Column(name = "registration_source", nullable = false)
  private RegistrationSource registrationSource;

  @Column(name = "registered_by")
  private UUID registeredBy;

  /** HMAC blind index of the normalised national ID (null when none given). */
  @Column(name = "national_id_hash")
  private String nationalIdHash;

  /** HMAC blind index of normalised name + DOB + phone (null when no phone). */
  @Column(name = "identity_hash")
  private String identityHash;

  @Column(name = "phone_verified_at")
  private Instant phoneVerifiedAt;

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  public boolean isPhoneVerified() {
    return phoneVerifiedAt != null;
  }

  public boolean isEmailVerified() {
    return emailVerifiedAt != null;
  }
}
