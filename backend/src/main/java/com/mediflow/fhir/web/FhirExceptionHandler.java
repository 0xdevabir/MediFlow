package com.mediflow.fhir.web;

import ca.uhn.fhir.parser.IParser;
import com.mediflow.fhir.support.FhirAccessDeniedException;
import com.mediflow.fhir.support.FhirBadRequestException;
import com.mediflow.fhir.support.FhirNotFoundException;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.mediflow.fhir.web")
public class FhirExceptionHandler {

  public static final String FHIR_JSON = "application/fhir+json";

  private final IParser parser;

  public FhirExceptionHandler(IParser parser) {
    this.parser = parser;
  }

  @ExceptionHandler(FhirNotFoundException.class)
  public ResponseEntity<String> notFound(FhirNotFoundException ex) {
    return outcome(HttpStatus.NOT_FOUND, OperationOutcome.IssueSeverity.ERROR, "not-found", ex.getMessage());
  }

  @ExceptionHandler(FhirAccessDeniedException.class)
  public ResponseEntity<String> denied(FhirAccessDeniedException ex) {
    return outcome(HttpStatus.FORBIDDEN, OperationOutcome.IssueSeverity.ERROR, "forbidden", ex.getMessage());
  }

  @ExceptionHandler(FhirBadRequestException.class)
  public ResponseEntity<String> badRequest(FhirBadRequestException ex) {
    return outcome(
        HttpStatus.BAD_REQUEST, OperationOutcome.IssueSeverity.ERROR, "invalid", ex.getMessage());
  }

  private ResponseEntity<String> outcome(
      HttpStatus status,
      OperationOutcome.IssueSeverity severity,
      String code,
      String diagnostics) {
    OperationOutcome oo = new OperationOutcome();
    oo.addIssue()
        .setSeverity(severity)
        .setCode(OperationOutcome.IssueType.fromCode(code.equals("not-found") ? "not-found" : code.equals("forbidden") ? "forbidden" : "invalid"))
        .setDetails(new CodeableConcept().setText(diagnostics))
        .setDiagnostics(diagnostics);
    return ResponseEntity.status(status)
        .contentType(MediaType.parseMediaType(FHIR_JSON))
        .body(parser.encodeResourceToString(oo));
  }
}
