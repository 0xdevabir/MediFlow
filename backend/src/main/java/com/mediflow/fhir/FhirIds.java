package com.mediflow.fhir;

import java.util.UUID;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;

public final class FhirIds {

  private FhirIds() {}

  public static String id(UUID uuid) {
    return uuid == null ? null : uuid.toString();
  }

  public static IdType idType(ResourceType type, UUID uuid) {
    return new IdType(type.name(), id(uuid));
  }

  public static Reference ref(ResourceType type, UUID uuid) {
    if (uuid == null) {
      return null;
    }
    return new Reference(type.name() + "/" + uuid);
  }

  public static Reference ref(ResourceType type, UUID uuid, String display) {
    Reference r = ref(type, uuid);
    if (r != null && display != null && !display.isBlank()) {
      r.setDisplay(display);
    }
    return r;
  }

  public static UUID parseUuid(String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    String raw = id.contains("/") ? id.substring(id.lastIndexOf('/') + 1) : id;
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
