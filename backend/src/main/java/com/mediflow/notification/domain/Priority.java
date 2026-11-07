package com.mediflow.notification.domain;

/** HIGH bypasses quiet hours and always adds an in-app copy (e.g. Red Alert, critical lab). */
public enum Priority {
  NORMAL,
  HIGH
}
