package com.mediflow.fhir.clinical;

import java.util.Locale;
import java.util.Map;

/**
 * Local LOINC/UCUM lookup for vitals (mirrors T05 clinical_vital_definition seed). Keeps T12 from
 * depending on the clinical module package.
 */
public final class VitalLoinc {

  private static final Map<String, String[]> BY_TYPE =
      Map.of(
          "BP", new String[] {"85354-9", "Blood pressure", "mm[Hg]"},
          "HR", new String[] {"8867-4", "Heart rate", "/min"},
          "RR", new String[] {"9279-1", "Respiratory rate", "/min"},
          "TEMP", new String[] {"8310-5", "Body temperature", "Cel"},
          "SPO2", new String[] {"59408-5", "Oxygen saturation", "%"},
          "MAP", new String[] {"8478-0", "Mean arterial pressure", "mm[Hg]"});

  private VitalLoinc() {}

  /** @return [loincCode, display, ucumUnit] */
  public static String[] forType(String vitalType) {
    if (vitalType == null) {
      return new String[] {"85353-1", "Vital signs", ""};
    }
    return BY_TYPE.getOrDefault(
        vitalType.trim().toUpperCase(Locale.ROOT),
        new String[] {"85353-1", vitalType, ""});
  }
}
