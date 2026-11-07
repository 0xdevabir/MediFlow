package com.mediflow.clinical.vitals.service;

import com.mediflow.clinical.vitals.domain.VitalDefinitionEntity;
import com.mediflow.clinical.vitals.domain.VitalType;
import com.mediflow.clinical.vitals.repo.VitalDefinitionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Data-driven vital types from {@code clinical_vital_definition}. */
@Service
public class VitalCatalogService {

  private final VitalDefinitionRepository definitions;
  private final Map<String, VitalType> byCode = new ConcurrentHashMap<>();

  public VitalCatalogService(VitalDefinitionRepository definitions) {
    this.definitions = definitions;
  }

  @Transactional(readOnly = true)
  public List<VitalType> nurseEntryTypes() {
    ensureLoaded();
    return definitions.findByActiveTrueAndNurseEntryTrueOrderBySortOrderAsc().stream()
        .map(VitalDefinitionEntity::toVitalType)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<VitalType> find(String code) {
    ensureLoaded();
    VitalType hit = byCode.get(code);
    if (hit != null) {
      return Optional.of(hit);
    }
    return definitions.findByCodeAndActiveTrue(code).map(VitalDefinitionEntity::toVitalType);
  }

  public VitalType require(String code) {
    return find(code)
        .orElseThrow(() -> new IllegalArgumentException("Unknown vital type: " + code));
  }

  /** Built-in fallbacks for MAP / shock index when the catalog row is missing. */
  public VitalType mapType() {
    return find(VitalType.MAP).orElse(VitalType.MAP_DEFAULT);
  }

  public VitalType shockIndexType() {
    return find(VitalType.SHOCK_INDEX).orElse(VitalType.SHOCK_INDEX_DEFAULT);
  }

  public void evict() {
    byCode.clear();
  }

  private void ensureLoaded() {
    if (!byCode.isEmpty()) {
      return;
    }
    synchronized (byCode) {
      if (!byCode.isEmpty()) {
        return;
      }
      Map<String, VitalType> loaded =
          definitions.findByActiveTrueOrderBySortOrderAsc().stream()
              .map(VitalDefinitionEntity::toVitalType)
              .collect(Collectors.toMap(VitalType::code, Function.identity(), (a, b) -> a));
      byCode.putAll(loaded);
    }
  }
}
