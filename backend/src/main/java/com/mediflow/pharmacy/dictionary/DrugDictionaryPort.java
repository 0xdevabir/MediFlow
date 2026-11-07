package com.mediflow.pharmacy.dictionary;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Brand-first DGDA drug dictionary (R&amp;D v1.3 §10): brand as written → registered product →
 * generic → WHO ATC / RxNorm.
 *
 * <p><b>Safety contract:</b> exact lookups never guess. An unknown or ambiguous name resolves to
 * empty; callers must treat that as "unknown, never safe" (&lt; 0.85, left blank). {@link
 * #suggest} returns fuzzy candidates for a human to pick from and must never be applied
 * automatically.
 *
 * <p>Defined in {@code com.mediflow.pharmacy.dictionary} because it did not exist in {@code
 * shared.port} yet; T00/T14 should promote it (and its records) to {@code com.mediflow.shared.port}.
 * Implemented by {@link DrugDictionaryService}.
 */
public interface DrugDictionaryPort {

  /** All registered products whose brand name equals {@code brandName} (normalised). */
  List<DrugMatch> findBrandProducts(String brandName);

  /**
   * Resolve a brand or generic name to its generic. Brand matches win (brand-first); if the brand
   * name maps to more than one generic, or nothing matches exactly, the result is empty.
   */
  Optional<GenericRef> resolveGeneric(String name);

  /** Fuzzy candidates (brands and generics), best first. Suggestions only — never auto-applied. */
  List<DrugMatch> suggest(String text, int limit);

  Optional<GenericRef> findGeneric(UUID genericId);

  Optional<DrugMatch> findBrand(UUID brandId);

  /** Every product of one generic (e.g. for a generic-name prescription or substitution). */
  List<DrugMatch> brandsOfGeneric(UUID genericId);

  /** Interaction rules between any two of the given generics. */
  List<InteractionRule> interactionsAmong(Collection<UUID> genericIds);

  /** Dose limits of one generic (see the report for the unit convention). */
  List<DoseLimitRule> doseLimits(UUID genericId);
}
