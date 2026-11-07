package com.mediflow.prescription.safety;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Patient facts the contradiction engine needs (loaded by the service layer). */
public record PatientSafetyContext(
    UUID patientId,
    LocalDate dateOfBirth,
    String sex,
    String pregnancyStatus,
    BigDecimal weightKg,
    List<AllergyRef> allergies,
    List<ConditionRef> conditions,
    List<ActiveMedRef> activeMeds,
    List<VitalRef> latestVitals) {

  public record AllergyRef(String substance, String substanceCode) {}

  public record ConditionRef(String icd10Code, String display) {}

  /** Active medicine from a recently signed prescription (generic level). */
  public record ActiveMedRef(UUID prescriptionId, UUID itemId, UUID genericId, String genericName) {}

  public record VitalRef(String vitalType, BigDecimal valueNumeric, String valueText) {}

  public int ageYears(LocalDate today) {
    if (dateOfBirth == null || today == null) {
      return -1;
    }
    return (int) java.time.temporal.ChronoUnit.YEARS.between(dateOfBirth, today);
  }

  public boolean pregnant() {
    if (pregnancyStatus == null) {
      return false;
    }
    String s = pregnancyStatus.trim().toLowerCase();
    return s.equals("pregnant") || s.equals("yes") || s.equals("true") || s.equals("1");
  }
}
