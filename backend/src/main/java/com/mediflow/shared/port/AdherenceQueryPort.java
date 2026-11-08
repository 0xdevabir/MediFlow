package com.mediflow.shared.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only adherence percent for patient dashboards and home tiles.
 *
 * <p><b>Implementor:</b> V2-10 ({@code com.mediflow.notification}). <b>Consumers:</b> V2-03 patient
 * dashboard, V2-01 role home.
 */
public interface AdherenceQueryPort {

  /**
   * @return adherence percent 0–100 when known; empty when the patient has no tracked regimen or
   *     the module is not configured
   */
  Optional<Integer> adherencePercent(UUID patientId);
}
