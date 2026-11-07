package com.mediflow.patient.service;

import java.util.List;
import java.util.UUID;

/** Registration matched an existing record (national ID, or name + DOB + phone). */
public class DuplicatePatientException extends PatientRuleException {

  public enum Kind {
    NATIONAL_ID,
    NAME_DOB_PHONE
  }

  private final Kind kind;
  private final List<UUID> candidatePatientIds;

  public DuplicatePatientException(Kind kind, List<UUID> candidatePatientIds) {
    super(kind == Kind.NATIONAL_ID ? "patient.duplicate.nationalId" : "patient.duplicate.nameDobPhone");
    this.kind = kind;
    this.candidatePatientIds = List.copyOf(candidatePatientIds);
  }

  public Kind kind() {
    return kind;
  }

  /** Only shown to front desk; never to a self-registering user. */
  public List<UUID> candidatePatientIds() {
    return candidatePatientIds;
  }
}
