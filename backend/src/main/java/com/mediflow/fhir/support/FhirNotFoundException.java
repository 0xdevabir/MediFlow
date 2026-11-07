package com.mediflow.fhir.support;

public class FhirNotFoundException extends RuntimeException {
  public FhirNotFoundException(String type, String id) {
    super(type + "/" + id + " not found");
  }
}
