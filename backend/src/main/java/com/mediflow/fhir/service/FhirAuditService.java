package com.mediflow.fhir.service;

import com.mediflow.fhir.auth.FhirClientAuthentication;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.web.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class FhirAuditService {

  private final AuditPort audit;
  private final CurrentUser currentUser;

  public FhirAuditService(AuditPort audit, CurrentUser currentUser) {
    this.audit = audit;
    this.currentUser = currentUser;
  }

  public void read(String resourceType, UUID resourceId, UUID patientId) {
    audit.record(
        actorId(),
        patientId,
        "FHIR_READ",
        resourceType,
        resourceId,
        Map.of("purpose", "fhir-export", "outcome", "success"));
  }

  public void denied(String resourceType, String id, UUID patientId, String reason) {
    audit.record(
        actorId(),
        patientId,
        "FHIR_READ_DENIED",
        resourceType,
        null,
        Map.of(
            "id",
            id == null ? "" : id,
            "reason",
            reason == null ? "" : reason,
            "outcome",
            "denied"));
  }

  private UUID actorId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof FhirClientAuthentication) {
      return ((FhirClientAuthentication) auth).clientRowId();
    }
    return currentUser.userId().orElse(null);
  }
}
