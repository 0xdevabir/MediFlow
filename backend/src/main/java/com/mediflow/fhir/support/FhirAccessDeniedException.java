package com.mediflow.fhir.support;

public class FhirAccessDeniedException extends RuntimeException {
  public FhirAccessDeniedException(String message) {
    super(message);
  }
}
