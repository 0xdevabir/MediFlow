package com.mediflow.fhir.service;

import com.mediflow.fhir.auth.FhirClientAuthentication;
import com.mediflow.fhir.repo.FhirDataStore;
import com.mediflow.fhir.support.FhirAccessDeniedException;
import com.mediflow.shared.domain.ConsentType;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.port.PatientAccessPort;
import com.mediflow.shared.web.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * RBAC + consent for FHIR reads. Machine clients require active {@link ConsentType#FHIR_SHARE};
 * clinicians use {@link PatientAccessPort}; patients may only read their own record; admins see all.
 */
@Component
public class FhirAccessGuard {

  private final CurrentUser currentUser;
  private final PatientAccessPort patientAccess;
  private final FhirDataStore data;

  public FhirAccessGuard(
      CurrentUser currentUser, PatientAccessPort patientAccess, FhirDataStore data) {
    this.currentUser = currentUser;
    this.patientAccess = patientAccess;
    this.data = data;
  }

  public void requirePatientRead(UUID patientId) {
    if (patientId == null) {
      throw new FhirAccessDeniedException("patient required");
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof FhirClientAuthentication) {
      if (!data.hasActiveConsent(patientId, ConsentType.FHIR_SHARE)) {
        throw new FhirAccessDeniedException("FHIR_SHARE consent required");
      }
      return;
    }
    if (currentUser.hasRole("ADMIN")) {
      return;
    }
    UUID userId =
        currentUser
            .userId()
            .orElseThrow(() -> new FhirAccessDeniedException("unauthenticated"));
    if (currentUser.hasRole("PATIENT")) {
      boolean own =
          data.patientByUserId(userId).map(Patient::getId).map(patientId::equals).orElse(false);
      if (!own) {
        throw new FhirAccessDeniedException("patients may only read own record");
      }
      return;
    }
    if (currentUser.hasRole("DOCTOR") || currentUser.hasRole("ER")) {
      if (!patientAccess.isAssigned(userId, patientId)
          && !data.hasActiveConsent(patientId, ConsentType.FHIR_SHARE)) {
        throw new FhirAccessDeniedException("not assigned to patient");
      }
      return;
    }
    if (currentUser.hasRole("NURSE")
        || currentUser.hasRole("FRONT_DESK")
        || currentUser.hasRole("LAB_TECH")
        || currentUser.hasRole("PHARMACIST")
        || currentUser.hasRole("CLINICAL_APPROVER")) {
      if (!data.hasActiveConsent(patientId, ConsentType.FHIR_SHARE)
          && !patientAccess.isAssigned(userId, patientId)) {
        throw new FhirAccessDeniedException("consent or assignment required");
      }
      return;
    }
    throw new FhirAccessDeniedException("role not permitted for FHIR read");
  }

  public void requireAuthenticated() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new FhirAccessDeniedException("unauthenticated");
    }
  }
}
