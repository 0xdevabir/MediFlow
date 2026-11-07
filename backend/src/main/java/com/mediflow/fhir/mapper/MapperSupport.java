package com.mediflow.fhir.mapper;

import com.mediflow.fhir.FhirSystems;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;

final class MapperSupport {

  private MapperSupport() {}

  static Date date(Instant instant) {
    return instant == null ? null : Date.from(instant);
  }

  static Date date(LocalDate localDate) {
    return localDate == null
        ? null
        : Date.from(localDate.atStartOfDay().toInstant(ZoneOffset.UTC));
  }

  static CodeableConcept coding(String system, String code, String display) {
    CodeableConcept cc = new CodeableConcept();
    Coding c = new Coding(system, code, display);
    cc.addCoding(c);
    if (display != null) {
      cc.setText(display);
    }
    return cc;
  }

  static CodeableConcept text(String text) {
    CodeableConcept cc = new CodeableConcept();
    cc.setText(text);
    return cc;
  }

  static Quantity quantity(java.math.BigDecimal value, String unit) {
    Quantity q = new Quantity();
    q.setValue(value);
    if (unit != null && !unit.isBlank()) {
      q.setUnit(unit);
      q.setSystem(FhirSystems.UCUM);
      q.setCode(unit);
    }
    return q;
  }

  static Extension dgdaBrand(String regNo) {
    if (regNo == null || regNo.isBlank()) {
      return null;
    }
    Extension ext = new Extension();
    ext.setUrl(FhirSystems.DGDA_BRAND);
    ext.setValue(new StringType(regNo));
    return ext;
  }
}

