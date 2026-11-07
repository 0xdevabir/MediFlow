package com.mediflow.prescription.domain;

/** How a draft was started: typed text (sent to the AI from stage 3), a photo, or fully manual. */
public enum RxInputMode {
  TEXT,
  PHOTO,
  MANUAL
}
