package com.mediflow.lab.service;

import com.mediflow.lab.repo.LabCatalogQueries;
import com.mediflow.lab.repo.LabHospitalQueries;
import com.mediflow.lab.repo.LabPackageQueries;
import com.mediflow.shared.domain.Hospital;
import com.mediflow.shared.domain.LabPackage;
import com.mediflow.shared.domain.LabPackageItem;
import com.mediflow.shared.domain.LabTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only catalogue of direct-allowed tests and packages. */
@Service
public class LabCatalogService {

  private final LabCatalogQueries tests;
  private final LabPackageQueries packages;
  private final LabHospitalQueries hospitals;

  public LabCatalogService(
      LabCatalogQueries tests, LabPackageQueries packages, LabHospitalQueries hospitals) {
    this.tests = tests;
    this.packages = packages;
    this.hospitals = hospitals;
  }

  @Transactional(readOnly = true)
  public List<Hospital> activeHospitals() {
    return hospitals.findByActiveTrueOrderByNameAsc();
  }

  @PreAuthorize("hasAnyRole('PATIENT','FRONT_DESK','DOCTOR','LAB_TECH','ADMIN')")
  @Transactional(readOnly = true)
  public List<LabViews.CatalogItem> directCatalog(UUID hospitalId) {
    List<LabViews.CatalogItem> out = new ArrayList<>();
    for (LabTest t : tests.findByActiveTrueAndDirectAllowedTrueOrderByNameAsc()) {
      out.add(
          new LabViews.CatalogItem(
              t.getId(),
              t.getCode(),
              t.getName(),
              t.getLoincCode(),
              t.getPrice() == null ? BigDecimal.ZERO : t.getPrice(),
              false,
              List.of()));
    }
    List<LabPackage> pkgs =
        hospitalId == null ? packages.findAllActive() : packages.findActiveForHospital(hospitalId);
    Map<UUID, LabTest> byId =
        tests.findByActiveTrueOrderByNameAsc().stream()
            .collect(Collectors.toMap(LabTest::getId, Function.identity(), (a, b) -> a));
    for (LabPackage p : pkgs) {
      List<LabPackageItem> items = packages.items(p.getId());
      if (!allDirectAllowed(items, byId)) {
        continue;
      }
      List<String> names =
          items.stream()
              .map(i -> byId.get(i.getTestId()))
              .filter(t -> t != null)
              .map(LabTest::getName)
              .toList();
      out.add(
          new LabViews.CatalogItem(
              p.getId(),
              "PKG-" + p.getId().toString().substring(0, 8),
              p.getName(),
              null,
              p.getPrice() == null ? BigDecimal.ZERO : p.getPrice(),
              true,
              names));
    }
    return out;
  }

  @PreAuthorize("hasRole('DOCTOR')")
  @Transactional(readOnly = true)
  public List<LabViews.CatalogItem> doctorCatalog() {
    return tests.findByActiveTrueOrderByNameAsc().stream()
        .map(
            t ->
                new LabViews.CatalogItem(
                    t.getId(),
                    t.getCode(),
                    t.getName(),
                    t.getLoincCode(),
                    t.getPrice() == null ? BigDecimal.ZERO : t.getPrice(),
                    false,
                    List.of()))
        .toList();
  }

  static boolean allDirectAllowed(List<LabPackageItem> items, Map<UUID, LabTest> byId) {
    if (items.isEmpty()) {
      return false;
    }
    for (LabPackageItem item : items) {
      LabTest t = byId.get(item.getTestId());
      if (t == null || !t.getActive() || !t.getDirectAllowed()) {
        return false;
      }
    }
    return true;
  }
}
