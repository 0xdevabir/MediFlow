package com.mediflow.fhir.support;

public class FhirBadRequestException extends RuntimeException {
  public FhirBadRequestException(String message) {
    super(message);
  }
}
