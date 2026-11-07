package com.mediflow.clinical.consultation.web;

import com.mediflow.clinical.consultation.domain.Icd10Entity;
import com.mediflow.clinical.consultation.service.Icd10SearchService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clinical/api")
public class ClinicalApiController {

  private final Icd10SearchService icd10;

  public ClinicalApiController(Icd10SearchService icd10) {
    this.icd10 = icd10;
  }

  @GetMapping("/icd10")
  @PreAuthorize("hasAnyRole('DOCTOR','ADMIN','NURSE')")
  public List<Map<String, String>> searchIcd(@RequestParam("q") String q) {
    List<Icd10Entity> hits = icd10.search(q, 20);
    List<Map<String, String>> out = new ArrayList<>();
    for (Icd10Entity e : hits) {
      Map<String, String> row = new LinkedHashMap<>();
      row.put("code", e.getCode());
      row.put("display", e.getDisplayEn());
      out.add(row);
    }
    return out;
  }
}
