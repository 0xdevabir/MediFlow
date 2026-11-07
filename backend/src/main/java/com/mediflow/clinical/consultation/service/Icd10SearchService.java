package com.mediflow.clinical.consultation.service;

import com.mediflow.clinical.consultation.domain.Icd10Entity;
import com.mediflow.clinical.consultation.repo.Icd10Repository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Icd10SearchService {

  private final Icd10Repository icd10;

  public Icd10SearchService(Icd10Repository icd10) {
    this.icd10 = icd10;
  }

  @Transactional(readOnly = true)
  public List<Icd10Entity> search(String query, int limit) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    String q = query.trim();
    List<Icd10Entity> hits = icd10.search(q);
    int cap = Math.max(1, Math.min(limit, 50));
    return hits.size() <= cap ? hits : hits.subList(0, cap);
  }
}
