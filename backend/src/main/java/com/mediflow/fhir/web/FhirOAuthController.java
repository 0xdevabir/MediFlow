package com.mediflow.fhir.web;

import com.mediflow.fhir.auth.FhirTokenService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fhir/r4/oauth")
public class FhirOAuthController {

  private final FhirTokenService tokens;

  public FhirOAuthController(FhirTokenService tokens) {
    this.tokens = tokens;
  }

  @PostMapping(
      path = "/token",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> token(@RequestParam MultiValueMap<String, String> form) {
    String grant = form.getFirst("grant_type");
    if (!"client_credentials".equals(grant)) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("error", "unsupported_grant_type"));
    }
    return tokens
        .issueClientCredentials(form.getFirst("client_id"), form.getFirst("client_secret"))
        .map(body -> ResponseEntity.ok().<Object>body(body))
        .orElseGet(
            () ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid_client")));
  }
}
