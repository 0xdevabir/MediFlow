package com.mediflow.prescription.safety;

import java.util.UUID;

/** Minimal view of a draft line as seen by the safety engine (generic-level). */
public record DraftItemView(
    UUID itemId,
    UUID brandId,
    UUID genericId,
    String brandName,
    String genericName,
    String dose,
    String route,
    String frequency,
    String duration,
    boolean knownInDictionary) {

  public static DraftItemView unknown(UUID itemId, String raw) {
    return new DraftItemView(itemId, null, null, raw, null, null, null, null, null, false);
  }
}
