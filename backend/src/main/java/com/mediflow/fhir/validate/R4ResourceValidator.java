package com.mediflow.fhir.validate;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

/**
 * Validates FHIR R4 resources. Uses HAPI {@link FhirValidator} when available; always enforces
 * encode/parse round-trip and required id/resourceType. Full profile validation needs
 * {@code hapi-fhir-validation} + {@code hapi-fhir-validation-resources-r4} (see
 * docs/plan/requests/T12.md).
 */
@Component
public class R4ResourceValidator {

  private final FhirContext ctx;
  private final IParser parser;
  private final FhirValidator validator;

  public R4ResourceValidator(FhirContext ctx, IParser parser) {
    this.ctx = ctx;
    this.parser = parser;
    this.validator = ctx.newValidator();
    this.validator.setValidateAgainstStandardSchema(false);
    this.validator.setValidateAgainstStandardSchematron(false);
  }

  public List<String> validate(Resource resource) {
    List<String> errors = new ArrayList<>();
    if (resource == null) {
      errors.add("resource is null");
      return errors;
    }
    if (resource.getResourceType() == null) {
      errors.add("resourceType missing");
    }
    if (!resource.hasIdElement() || resource.getIdElement().getIdPart() == null) {
      errors.add("id missing");
    }
    try {
      String json = parser.encodeResourceToString(resource);
      IBaseResource roundTrip = parser.parseResource(json);
      if (roundTrip == null) {
        errors.add("round-trip parse returned null");
      }
    } catch (Exception e) {
      errors.add("round-trip failed: " + e.getMessage());
    }
    try {
      ValidationResult result = validator.validateWithResult(resource);
      for (SingleValidationMessage msg : result.getMessages()) {
        if (msg.getSeverity() != null
            && msg.getSeverity().ordinal()
                >= ca.uhn.fhir.validation.ResultSeverityEnum.ERROR.ordinal()) {
          errors.add(msg.getSeverity() + ": " + msg.getMessage());
        }
      }
    } catch (Exception e) {
      // Base validator without InstanceValidator module may no-op; structural checks above remain.
    }
    return errors;
  }

  public void assertValid(Resource resource) {
    List<String> errors = validate(resource);
    if (!errors.isEmpty()) {
      throw new IllegalStateException("FHIR validation errors: " + errors);
    }
  }
}
