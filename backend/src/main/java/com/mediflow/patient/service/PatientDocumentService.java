package com.mediflow.patient.service;

import com.mediflow.patient.domain.DocumentType;
import com.mediflow.patient.domain.PatientDocument;
import com.mediflow.patient.repo.PatientDocumentRepository;
import com.mediflow.patient.repo.PatientLookupRepository;
import com.mediflow.shared.port.ObjectStoragePort;
import com.mediflow.shared.web.CurrentUser;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PatientDocumentService {

  private static final long MAX_BYTES = 5 * 1024 * 1024;
  private static final Set<String> ALLOWED_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

  private final PatientLookupRepository patients;
  private final PatientDocumentRepository documents;
  private final ObjectStoragePort storage;
  private final PatientAudit audit;
  private final CurrentUser currentUser;

  public PatientDocumentService(
      PatientLookupRepository patients,
      PatientDocumentRepository documents,
      ObjectStoragePort storage,
      PatientAudit audit,
      CurrentUser currentUser) {
    this.patients = patients;
    this.documents = documents;
    this.storage = storage;
    this.audit = audit;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("@patientAccess.canAccessDocuments(#patientId)")
  public List<PatientDocument> list(UUID patientId) {
    requirePatient(patientId);
    audit.record(patientId, "DOCUMENT_LIST", PatientAudit.DOCUMENT, patientId);
    return documents.findByPatientIdOrderByCreatedAtDesc(patientId);
  }

  @Transactional
  @PreAuthorize("@patientAccess.canAccessDocuments(#patientId)")
  public PatientDocument upload(UUID patientId, DocumentType type, MultipartFile file) {
    requirePatient(patientId);
    if (type == null || file == null || file.isEmpty()) {
      throw new PatientRuleException("patient.document.incomplete");
    }
    if (file.getSize() > MAX_BYTES) {
      throw new PatientRuleException("patient.document.tooLarge");
    }
    String contentType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
    if (!ALLOWED_TYPES.contains(contentType)) {
      throw new PatientRuleException("patient.document.typeUnsupported");
    }
    String key =
        "patients/"
            + patientId
            + "/documents/"
            + UUID.randomUUID()
            + extensionFor(contentType);
    try (InputStream in = file.getInputStream()) {
      storage.put(key, in, file.getSize(), contentType);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read upload", e);
    }
    PatientDocument doc = new PatientDocument();
    doc.setPatientId(patientId);
    doc.setDocumentType(type);
    doc.setObjectKey(key);
    doc.setContentType(contentType);
    doc.setSizeBytes(file.getSize());
    doc.setOriginalFilename(sanitizeFilename(file.getOriginalFilename()));
    doc.setUploadedBy(currentUser.userId().orElse(null));
    documents.save(doc);
    audit.record(
        patientId,
        "DOCUMENT_UPLOADED",
        PatientAudit.DOCUMENT,
        doc.getId(),
        Map.of("type", type.name(), "bytes", file.getSize()));
    return doc;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("@patientAccess.canAccessDocuments(#patientId)")
  public Optional<InputStream> open(UUID patientId, UUID documentId) {
    PatientDocument doc =
        documents
            .findById(documentId)
            .filter(d -> d.getPatientId().equals(patientId))
            .orElseThrow(() -> new PatientRuleException("patient.document.notFound"));
    audit.record(patientId, "DOCUMENT_READ", PatientAudit.DOCUMENT, doc.getId());
    return storage.get(doc.getObjectKey());
  }

  private void requirePatient(UUID patientId) {
    patients.findById(patientId).orElseThrow(() -> new PatientRuleException("patient.notFound"));
  }

  private static String extensionFor(String contentType) {
    return switch (contentType) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      case "application/pdf" -> ".pdf";
      default -> "";
    };
  }

  private static String sanitizeFilename(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    String base = name.replace('\\', '/');
    int slash = base.lastIndexOf('/');
    if (slash >= 0) {
      base = base.substring(slash + 1);
    }
    return base.length() > 255 ? base.substring(0, 255) : base;
  }
}
