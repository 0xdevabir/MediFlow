package com.mediflow.patient.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "patient_document")
@Getter
@Setter
public class PatientDocument extends BaseEntity {

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false)
  private DocumentType documentType;

  @Column(name = "object_key", nullable = false, unique = true)
  private String objectKey;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "original_filename")
  private String originalFilename;

  @Column(name = "uploaded_by")
  private UUID uploadedBy;
}
