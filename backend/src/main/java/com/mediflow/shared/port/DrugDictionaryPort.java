package com.mediflow.shared.port;

import com.mediflow.shared.domain.ConflictSeverity;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Brand-first DGDA drug dictionary (R&amp;D v1.3 §10): brand as written → registered product →
 * generic → WHO ATC / RxNorm.
 *
 * <p><b>Safety contract:</b> exact lookups never guess. Unknown or ambiguous names resolve to empty;
 * callers must treat that as "unknown, never safe" (&lt; 0.85, left blank). {@link #suggestBrands}
 * returns fuzzy candidates for a human to pick and must never be applied automatically.
 *
 * <p><b>Implementor:</b> V2-09 ({@code com.mediflow.pharmacy.dictionary}). <b>Consumers:</b> V2-07
 * prescription safety, V2-09 dispense.
 */
public interface DrugDictionaryPort {

  /** All registered products whose brand name equals {@code brandName} (normalised). */
  List<DrugMatch> findBrandProducts(String brandName);

  /** Fuzzy brand/generic candidates, best first. Suggestions only — never auto-applied. */
  List<DrugMatch> suggestBrands(String query);

  Optional<GenericRef> findGeneric(UUID genericId);

  Optional<GenericRef> findGenericByName(String name);

  /** Interaction rules between any pair among the given generics. */
  List<InteractionRule> interactionsAmong(Collection<UUID> genericIds);

  Optional<InteractionRule> interaction(UUID genericA, UUID genericB);

  List<DoseLimitRule> doseLimits(UUID genericId);

  /** Exact brand-by-id lookup for dispense / substitution. Empty when unknown — never guess. */
  Optional<DrugMatch> findBrand(UUID brandId);

  /**
   * One dictionary hit. {@code brandId} is null when the hit is a generic name. {@code score} is
   * 1.0 for exact hits and similarity for suggestions. {@code illustrative} marks seeded sample
   * rows that are not from the DGDA register.
   */
  record DrugMatch(
      UUID brandId,
      String brandName,
      String strength,
      String form,
      String dgdaRegNo,
      UUID genericId,
      String genericName,
      String atc,
      String rxnorm,
      MatchType matchType,
      double score,
      boolean illustrative) {

    public enum MatchType {
      EXACT_BRAND,
      EXACT_GENERIC,
      SUGGESTION
    }
  }

  record GenericRef(UUID id, String name, String atcCode, String rxnormCode, boolean illustrative) {}

  /**
   * {@code placeholder} = curated data that still needs a licensed source. Severity uses the shared
   * {@link ConflictSeverity} vocabulary.
   */
  record InteractionRule(
      UUID genericAId,
      UUID genericBId,
      ConflictSeverity severity,
      String description,
      boolean placeholder) {}

  /**
   * population ADULT | PAEDIATRIC; unit {@code mg}, {@code mg/day}, {@code mg/kg}, {@code
   * mg/kg/day}; bounds inclusive, null = unbounded. {@code placeholder} = needs a licensed source.
   */
  record DoseLimitRule(
      UUID genericId,
      String population,
      String route,
      BigDecimal minDose,
      BigDecimal maxDose,
      String unit,
      boolean placeholder) {}
}
