package com.mediflow.prescription.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** T06-owned extension of {@code prescription} (input mode, photo, addendum link, revoke data). */
@Entity
@Table(name = "rx_prescription_meta")
@Getter
@Setter
public class RxPrescriptionMeta extends BaseEntity {

  @Column(name = "prescription_id", nullable = false, unique = true)
  private UUID prescriptionId;

  @Column(name = "doctor_user_id", nullable = false)
  private UUID doctorUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "input_mode", nullable = false)
  private RxInputMode inputMode;

  @Column(name = "photo_object_key")
  private String photoObjectKey;

  @Column(name = "photo_content_type")
  private String photoContentType;

  @Column(name = "typed_text", columnDefinition = "TEXT")
  private String typedText;

  @Column(name = "amends_prescription_id")
  private UUID amendsPrescriptionId;

  @Column(name = "revoke_reason", columnDefinition = "TEXT")
  private String revokeReason;

  @Column(name = "revoked_by")
  private UUID revokedBy;
}
