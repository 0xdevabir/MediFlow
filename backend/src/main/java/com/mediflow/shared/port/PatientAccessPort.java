package com.mediflow.shared.port;

import java.util.UUID;

public interface PatientAccessPort {

  /** Doctors see assigned patients only (deny by default). */
  boolean isAssigned(UUID doctorId, UUID patientId);
}
