package com.mediflow.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FhirConfig {

  @Bean
  FhirContext fhirContext() {
    return FhirContext.forR4Cached();
  }

  @Bean
  IParser fhirJsonParser(FhirContext ctx) {
    IParser parser = ctx.newJsonParser();
    parser.setPrettyPrint(false);
    return parser;
  }
}
