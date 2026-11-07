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

@Entity
@Table(name = "patient_contact_verification")
@Getter
@Setter
public class ContactVerification extends BaseEntity {

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false)
  private ContactChannel channel;

  @Column(name = "code_hash", nullable = false)
  private String codeHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "consumed_at")
  private Instant consumedAt;
}
