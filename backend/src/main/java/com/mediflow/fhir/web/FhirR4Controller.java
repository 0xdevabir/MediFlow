package com.mediflow.fhir.web;

import ca.uhn.fhir.parser.IParser;
import com.mediflow.fhir.FhirIds;
import com.mediflow.fhir.service.CapabilityStatementFactory;
import com.mediflow.fhir.service.FhirEverythingService;
import com.mediflow.fhir.service.FhirReadService;
import com.mediflow.fhir.support.FhirBadRequestException;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fhir/r4")
public class FhirR4Controller {

  private final FhirReadService reads;
  private final FhirEverythingService everything;
  private final CapabilityStatementFactory capability;
  private final IParser parser;

  public FhirR4Controller(
      FhirReadService reads,
      FhirEverythingService everything,
      CapabilityStatementFactory capability,
      IParser parser) {
    this.reads = reads;
    this.everything = everything;
    this.capability = capability;
    this.parser = parser;
  }

  @GetMapping(path = "/metadata", produces = {FhirExceptionHandler.FHIR_JSON, MediaType.APPLICATION_JSON_VALUE})
  public ResponseEntity<String> metadata() {
    CapabilityStatement cs = capability.build();
    return json(cs);
  }

  @GetMapping(
      path = "/{type}/{id}",
      produces = {FhirExceptionHandler.FHIR_JSON, MediaType.APPLICATION_JSON_VALUE})
  public ResponseEntity<String> read(@PathVariable String type, @PathVariable String id) {
    if (!FhirReadService.isSupported(type)) {
      throw new FhirBadRequestException("unsupported resource type: " + type);
    }
    Resource resource = reads.read(type, id);
    return json(resource);
  }

  @GetMapping(
      path = {"/Patient/{id}/$everything", "/Patient/{id}/everything"},
      produces = {FhirExceptionHandler.FHIR_JSON, MediaType.APPLICATION_JSON_VALUE})
  public ResponseEntity<String> patientEverything(@PathVariable String id) {
    UUID patientId = FhirIds.parseUuid(id);
    if (patientId == null) {
      throw new FhirBadRequestException("invalid patient id");
    }
    Bundle bundle = everything.everything(patientId);
    return json(bundle);
  }

  private ResponseEntity<String> json(Resource resource) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(FhirExceptionHandler.FHIR_JSON))
        .body(parser.encodeResourceToString(resource));
  }
}
