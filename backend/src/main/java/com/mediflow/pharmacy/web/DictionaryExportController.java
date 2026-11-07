package com.mediflow.pharmacy.web;

import com.mediflow.pharmacy.dictionary.DrugDictionaryService;
import com.mediflow.pharmacy.dictionary.DrugDictionaryService.DictionarySnapshot;
import com.mediflow.pharmacy.dictionary.DrugMatch;
import com.mediflow.pharmacy.dictionary.GenericRef;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service export for T09's matcher. Auth is the internal key filter on {@code
 * /api/internal/dictionary/**} (see {@code PharmacyInternalSecurityConfig}).
 */
@RestController
@RequestMapping("/api/internal/dictionary")
public class DictionaryExportController {

  private final DrugDictionaryService dictionary;

  public DictionaryExportController(DrugDictionaryService dictionary) {
    this.dictionary = dictionary;
  }

  @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> export() {
    DictionarySnapshot snap = dictionary.snapshot();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("importVersion", snap.importVersion());
    body.put("generics", snap.generics().stream().map(this::genericJson).toList());
    body.put("brands", snap.brands().stream().map(this::brandJson).toList());
    return body;
  }

  private Map<String, Object> genericJson(GenericRef g) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", g.id());
    m.put("name", g.name());
    m.put("atcCode", g.atcCode());
    m.put("rxnormCode", g.rxnormCode());
    m.put("illustrative", g.illustrative());
    return m;
  }

  private Map<String, Object> brandJson(DrugMatch b) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("brandId", b.brandId());
    m.put("brandName", b.brandName());
    m.put("strength", b.strength());
    m.put("form", b.form());
    m.put("dgdaRegNo", b.dgdaRegNo());
    m.put("genericId", b.genericId());
    m.put("genericName", b.genericName());
    m.put("atcCode", b.atcCode());
    m.put("rxnormCode", b.rxnormCode());
    m.put("illustrative", b.illustrative());
    return m;
  }
}

