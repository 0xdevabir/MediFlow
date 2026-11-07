package com.mediflow.identity.access;

/** Which patients a granted cell applies to. */
public enum AccessScope {
  /** Patient: own record only. */
  OWN,
  /** Doctor: assigned patients only (checked in today, active referral or break-glass). */
  ASSIGNED,
  /** ER: doctor-level access limited to the Red-Alert patient (or break-glass). */
  ER_ASSIGNED,
  /** Lab tech: only patients with a lab order they handle (minimum necessary). */
  LINKED_LAB_ORDER,
  /** Pharmacist: only patients with a signed prescription they handle (minimum necessary). */
  LINKED_PRESCRIPTION,
  /** Nurse / front desk: operational access to any patient (minimum data where marked). */
  STAFF,
  /** Admin: aggregated / anonymised data only, never one patient's record. */
  AGGREGATE,
  /** Not bound to a patient (directory, settings) or whole log (admin audit). */
  GLOBAL
}
