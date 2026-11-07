package com.mediflow.patient.domain;

/** Who entered an allergy or history item. Only {@link #CLINICIAN} entries start verified. */
public enum ClinicalSource {
  SELF_REPORTED,
  FRONT_DESK,
  CLINICIAN
}
