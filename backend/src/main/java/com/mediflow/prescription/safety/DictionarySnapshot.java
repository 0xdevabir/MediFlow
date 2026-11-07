package com.mediflow.prescription.safety;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Immutable in-memory dictionary (the engine runs in memory, DOCX §10.5: p95 ≤ 300 ms). */
public final class DictionarySnapshot implements RxDrugDictionary {

  private final Map<UUID, GenericInfo> generics = new HashMap<>();
  private final Map<String, GenericInfo> genericsByName = new HashMap<>();
  private final Map<UUID, BrandInfo> brands = new HashMap<>();
  private final Map<String, List<BrandInfo>> brandsByName = new HashMap<>();
  private final Map<String, InteractionInfo> interactions = new HashMap<>();
  private final Map<UUID, List<DoseLimitInfo>> limits = new HashMap<>();
  private final List<BrandInfo> sortedBrands;

  public DictionarySnapshot(
      Collection<GenericInfo> generics,
      Collection<BrandInfo> brands,
      Collection<InteractionInfo> interactions,
      Collection<DoseLimitInfo> limits) {
    for (GenericInfo g : generics) {
      this.generics.put(g.id(), g);
      genericsByName.put(norm(g.name()), g);
    }
    for (BrandInfo b : brands) {
      this.brands.put(b.id(), b);
      brandsByName.computeIfAbsent(norm(b.name()), k -> new ArrayList<>()).add(b);
    }
    for (InteractionInfo i : interactions) {
      String key = pairKey(i.genericAId(), i.genericBId());
      InteractionInfo existing = this.interactions.get(key);
      // keep the most severe entry for a pair
      if (existing == null || i.severity().ordinal() < existing.severity().ordinal()) {
        this.interactions.put(key, i);
      }
    }
    for (DoseLimitInfo l : limits) {
      this.limits.computeIfAbsent(l.genericId(), k -> new ArrayList<>()).add(l);
    }
    sortedBrands =
        brands.stream().sorted(Comparator.comparing(b -> norm(b.name()))).toList();
  }

  public static String norm(String s) {
    return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private static String pairKey(UUID a, UUID b) {
    return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
  }

  @Override
  public Optional<GenericInfo> generic(UUID genericId) {
    return genericId == null ? Optional.empty() : Optional.ofNullable(generics.get(genericId));
  }

  @Override
  public Optional<BrandInfo> brand(UUID brandId) {
    return brandId == null ? Optional.empty() : Optional.ofNullable(brands.get(brandId));
  }

  @Override
  public List<BrandInfo> brandsByName(String name) {
    return List.copyOf(brandsByName.getOrDefault(norm(name), List.of()));
  }

  @Override
  public Optional<GenericInfo> genericByName(String name) {
    return Optional.ofNullable(genericsByName.get(norm(name)));
  }

  @Override
  public Optional<InteractionInfo> interaction(UUID genericA, UUID genericB) {
    if (genericA == null || genericB == null || genericA.equals(genericB)) {
      return Optional.empty();
    }
    return Optional.ofNullable(interactions.get(pairKey(genericA, genericB)));
  }

  @Override
  public List<DoseLimitInfo> doseLimits(UUID genericId) {
    return List.copyOf(limits.getOrDefault(genericId, List.of()));
  }

  @Override
  public List<BrandInfo> searchBrands(String query, int limit) {
    String q = norm(query);
    if (q.isEmpty()) {
      return List.of();
    }
    return sortedBrands.stream()
        .filter(
            b ->
                norm(b.name()).startsWith(q)
                    || generic(b.genericId()).map(g -> norm(g.name()).startsWith(q)).orElse(false))
        .limit(limit)
        .toList();
  }
}
