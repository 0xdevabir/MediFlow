package com.mediflow.clinical.consultation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.clinical.consultation.port.BridgedClinicalAccess;
import com.mediflow.clinical.consultation.port.ClinicalAccess;
import com.mediflow.identity.access.AccessPolicy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BridgedClinicalAccessTest {

  @Test
  void prefersAccessPolicyWhenMatrixAllows() {
    AccessPolicy policy = mock(AccessPolicy.class);
    ClinicalAccess fallback = mock(ClinicalAccess.class);
    UUID patientId = UUID.randomUUID();
    when(policy.isAssigned(patientId)).thenReturn(true);
    when(fallback.canConsult(any())).thenReturn(false);

    BridgedClinicalAccess bridged = new BridgedClinicalAccess(policy, fallback);
    assertTrue(bridged.canConsult(patientId));
  }

  @Test
  void fallsBackWhenMatrixDenies() {
    AccessPolicy policy = mock(AccessPolicy.class);
    ClinicalAccess fallback = mock(ClinicalAccess.class);
    UUID patientId = UUID.randomUUID();
    when(policy.isAssigned(patientId)).thenReturn(false);
    when(policy.can(eq("CONSULTATION_NOTES"), eq("C"), eq(patientId))).thenReturn(false);
    when(policy.can(eq("CONSULTATION_NOTES"), eq("U"), eq(patientId))).thenReturn(false);
    when(fallback.canConsult(patientId)).thenReturn(true);

    BridgedClinicalAccess bridged = new BridgedClinicalAccess(policy, fallback);
    assertTrue(bridged.canConsult(patientId));
  }

  @Test
  void deniesWhenBothDeny() {
    AccessPolicy policy = mock(AccessPolicy.class);
    ClinicalAccess fallback = mock(ClinicalAccess.class);
    UUID patientId = UUID.randomUUID();
    when(fallback.canOrderLab(patientId)).thenReturn(false);

    BridgedClinicalAccess bridged = new BridgedClinicalAccess(policy, fallback);
    assertFalse(bridged.canOrderLab(patientId));
  }
}
