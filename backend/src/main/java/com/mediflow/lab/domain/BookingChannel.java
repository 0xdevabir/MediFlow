package com.mediflow.lab.domain;

/** Who placed a lab order. DIRECT orders come from PATIENT or FRONT_DESK; DOCTOR orders from DOCTOR. */
public enum BookingChannel {
  PATIENT,
  FRONT_DESK,
  DOCTOR
}
