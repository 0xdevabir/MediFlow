package com.mediflow.prescription.safety;

import com.mediflow.shared.domain.ConflictSeverity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Drug dictionary as seen by the prescription module (generic level). T08 owns the data; the
 * default implementation {@link RepositoryRxDrugDictionary} reads the shared drug_* tables. T08
 * may later provide its own (Redis-cached) bean of this type.
 */
public interface RxDrugDictionary {

  record GenericInfo(UUID id, String name, String atcCode) {}

  record BrandInfo(
      UUID id, String name, UUID genericId, String strength, String form, String dgdaRegNo) {}

  record InteractionInfo(
      UUID genericAId, UUID genericBId, ConflictSeverity severity, String description) {}

  /**
   * Dose limit for one generic and population. {@code unit} like {@code mg}, {@code mg/day},
   * {@code mg/kg}, {@code mg/kg/day}: "/kg" scales by body weight, "/day" compares the daily total.
   */
  record DoseLimitInfo(
      UUID genericId,
      String population,
      String route,
      BigDecimal minDose,
      BigDecimal maxDose,
      String unit) {}

  Optional<GenericInfo> generic(UUID genericId);

  Optional<BrandInfo> brand(UUID brandId);

  /** Exact (case/space-insensitive) brand name match; several strengths may share one name. */
  List<BrandInfo> brandsByName(String name);

  Optional<GenericInfo> genericByName(String name);

  Optional<InteractionInfo> interaction(UUID genericA, UUID genericB);

  List<DoseLimitInfo> doseLimits(UUID genericId);

  /** Prefix search for the manual item editor. */
  List<BrandInfo> searchBrands(String query, int limit);
}
