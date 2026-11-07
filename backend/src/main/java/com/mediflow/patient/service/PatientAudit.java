package com.mediflow.patient.service;

import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.web.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Thin wrapper so every patient-module read/write is audited the same way. */
@Component
public class PatientAudit {

  public static final String PATIENT = "Patient";
  public static final String ALLERGY = "Allergy";
  public static final String HISTORY = "PatientHistoryItem";
  public static final String DOCUMENT = "PatientDocument";
  public static final String CHANGE_REQUEST = "PatientDemographicChange";
  public static final String CONTACT_VERIFICATION = "PatientContactVerification";

  private final AuditPort audit;
  private final CurrentUser currentUser;

  public PatientAudit(AuditPort audit, CurrentUser currentUser) {
    this.audit = audit;
    this.currentUser = currentUser;
  }

  public void record(UUID patientId, String action, String resourceType, UUID resourceId) {
    record(patientId, action, resourceType, resourceId, Map.of());
  }

  public void record(
      UUID patientId, String action, String resourceType, UUID resourceId, Map<String, Object> details) {
    audit.record(currentUser.userId().orElse(null), patientId, action, resourceType, resourceId, details);
  }

  /** For flows without a signed-in user yet (self sign-up): the new account is the actor. */
  public void recordAs(
      UUID actor, UUID patientId, String action, String resourceType, UUID resourceId, Map<String, Object> details) {
    audit.record(actor, patientId, action, resourceType, resourceId, details);
  }
}
