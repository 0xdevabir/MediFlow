package com.mediflow.appointment.queue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mediflow.appointment.support.AppointmentException;
import com.mediflow.shared.domain.Patient;
import org.junit.jupiter.api.Test;

class CheckinIdentityTest {

  @Test
  void acceptsMrnAndPhone() {
    Patient p = patient("MRN-1", "1234567890", "01711111111");
    assertDoesNotThrow(() -> CheckinService.verifyTwoIdentifiers(p, "mrn-1", "01711-111111"));
  }

  @Test
  void rejectsSameIdentifierTwice() {
    Patient p = patient("MRN-1", "123", "017");
    AppointmentException ex =
        assertThrows(
            AppointmentException.class,
            () -> CheckinService.verifyTwoIdentifiers(p, "MRN-1", "mrn-1"));
    assertEquals("appointment.error.identifiersDistinct", ex.messageKey());
  }

  @Test
  void rejectsMismatch() {
    Patient p = patient("MRN-1", "123", "017");
    AppointmentException ex =
        assertThrows(
            AppointmentException.class,
            () -> CheckinService.verifyTwoIdentifiers(p, "MRN-1", "999"));
    assertEquals("appointment.error.identityMismatch", ex.messageKey());
  }

  @Test
  void formatsToken() {
    assertEquals("T001", CheckinService.formatToken(1));
    assertEquals("T042", CheckinService.formatToken(42));
  }

  private static Patient patient(String mrn, String nid, String phone) {
    Patient p = new Patient();
    p.setMrn(mrn);
    p.setNationalId(nid);
    p.setPhone(phone);
    p.setGivenName("A");
    p.setFamilyName("B");
    return p;
  }
}
