package com.mediflow.prescription.safety;

import com.mediflow.shared.domain.ConflictSeverity;
import com.mediflow.shared.domain.DrugBrand;
import com.mediflow.shared.domain.DrugDoseLimit;
import com.mediflow.shared.domain.DrugGeneric;
import com.mediflow.shared.domain.DrugInteraction;
import com.mediflow.shared.port.DrugDictionaryPort;
import com.mediflow.shared.repo.DrugBrandRepository;
import com.mediflow.shared.repo.DrugDoseLimitRepository;
import com.mediflow.shared.repo.DrugGenericRepository;
import com.mediflow.shared.repo.DrugInteractionRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Safe default for {@link DrugDictionaryPort} until V2-09's pharmacy bean is present. Reads the
 * shared {@code drug_*} tables (same source as the former {@link RepositoryRxDrugDictionary}).
 */
@Component
@ConditionalOnMissingBean(DrugDictionaryPort.class)
public class RepositoryDrugDictionaryPort implements DrugDictionaryPort {

  private static final Duration TTL = Duration.ofSeconds(60);

  private final DrugGenericRepository generics;
  private final DrugBrandRepository brands;
  private final DrugInteractionRepository interactions;
  private final DrugDoseLimitRepository limits;

  private volatile Snapshot snapshot;
  private volatile long loadedAtNanos;

  public RepositoryDrugDictionaryPort(
      DrugGenericRepository generics,
      DrugBrandRepository brands,
      DrugInteractionRepository interactions,
      DrugDoseLimitRepository limits) {
    this.generics = generics;
    this.brands = brands;
    this.interactions = interactions;
    this.limits = limits;
  }

  /** Forces a reload on next access (e.g. after a DGDA import). */
  public void invalidate() {
    snapshot = null;
  }

  @Transactional(readOnly = true)
  Snapshot snapshot() {
    Snapshot s = snapshot;
    if (s == null || System.nanoTime() - loadedAtNanos > TTL.toNanos()) {
      synchronized (this) {
        if (snapshot == null || System.nanoTime() - loadedAtNanos > TTL.toNanos()) {
          snapshot = load();
          loadedAtNanos = System.nanoTime();
        }
        s = snapshot;
      }
    }
    return s;
  }

  private Snapshot load() {
    List<DrugGeneric> genericRows = generics.findAll();
    List<DrugBrand> brandRows = brands.findAll();
    Map<UUID, DrugGeneric> genericById = new HashMap<>();
    Map<String, DrugGeneric> genericByName = new HashMap<>();
    for (DrugGeneric g : genericRows) {
      genericById.put(g.getId(), g);
      genericByName.put(norm(g.getName()), g);
    }
    Map<UUID, DrugBrand> brandById = new HashMap<>();
    Map<String, List<DrugBrand>> brandsByName = new HashMap<>();
    for (DrugBrand b : brandRows) {
      brandById.put(b.getId(), b);
      brandsByName.computeIfAbsent(norm(b.getName()), k -> new ArrayList<>()).add(b);
    }
    List<DrugInteraction> ix = interactions.findAll();
    Map<UUID, List<DrugDoseLimit>> limitsByGeneric = new HashMap<>();
    for (DrugDoseLimit l : limits.findAll()) {
      limitsByGeneric.computeIfAbsent(l.getGenericId(), k -> new ArrayList<>()).add(l);
    }
    return new Snapshot(genericById, genericByName, brandById, brandsByName, ix, limitsByGeneric);
  }

  @Override
  public List<DrugMatch> findBrandProducts(String brandName) {
    if (brandName == null || brandName.isBlank()) {
      return List.of();
    }
    Snapshot s = snapshot();
    List<DrugBrand> hits = s.brandsByName.getOrDefault(norm(brandName), List.of());
    return hits.stream().map(b -> toMatch(b, s, DrugMatch.MatchType.EXACT_BRAND, 1.0)).toList();
  }

  @Override
  public List<DrugMatch> suggestBrands(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    Snapshot s = snapshot();
    String q = norm(query);
    List<DrugMatch> out = new ArrayList<>();
    for (DrugBrand b : s.brandById.values()) {
      String name = norm(b.getName());
      if (name.startsWith(q) || name.contains(q)) {
        double score = name.startsWith(q) ? 0.9 : 0.7;
        out.add(toMatch(b, s, DrugMatch.MatchType.SUGGESTION, score));
      }
    }
    for (DrugGeneric g : s.genericById.values()) {
      String name = norm(g.getName());
      if (name.startsWith(q) || name.contains(q)) {
        double score = name.startsWith(q) ? 0.85 : 0.65;
        out.add(
            new DrugMatch(
                null,
                g.getName(),
                null,
                null,
                null,
                g.getId(),
                g.getName(),
                g.getAtcCode(),
                g.getRxnormCode(),
                DrugMatch.MatchType.SUGGESTION,
                score,
                false));
      }
    }
    out.sort(Comparator.comparingDouble(DrugMatch::score).reversed());
    return out.size() > 25 ? List.copyOf(out.subList(0, 25)) : List.copyOf(out);
  }

  @Override
  public Optional<GenericRef> findGeneric(UUID genericId) {
    if (genericId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(snapshot().genericById.get(genericId)).map(this::toRef);
  }

  @Override
  public Optional<GenericRef> findGenericByName(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(snapshot().genericByName.get(norm(name))).map(this::toRef);
  }

  @Override
  public List<InteractionRule> interactionsAmong(Collection<UUID> genericIds) {
    if (genericIds == null || genericIds.size() < 2) {
      return List.of();
    }
    Set<UUID> set = new HashSet<>(genericIds);
    List<InteractionRule> out = new ArrayList<>();
    for (DrugInteraction i : snapshot().interactions) {
      if (set.contains(i.getGenericAId()) && set.contains(i.getGenericBId())) {
        out.add(toRule(i));
      }
    }
    return List.copyOf(out);
  }

  @Override
  public Optional<InteractionRule> interaction(UUID genericA, UUID genericB) {
    if (genericA == null || genericB == null) {
      return Optional.empty();
    }
    for (DrugInteraction i : snapshot().interactions) {
      if ((i.getGenericAId().equals(genericA) && i.getGenericBId().equals(genericB))
          || (i.getGenericAId().equals(genericB) && i.getGenericBId().equals(genericA))) {
        return Optional.of(toRule(i));
      }
    }
    return Optional.empty();
  }

  @Override
  public List<DoseLimitRule> doseLimits(UUID genericId) {
    if (genericId == null) {
      return List.of();
    }
    List<DrugDoseLimit> rows = snapshot().limitsByGeneric.getOrDefault(genericId, List.of());
    return rows.stream()
        .map(
            l ->
                new DoseLimitRule(
                    l.getGenericId(),
                    l.getPopulation(),
                    l.getRoute(),
                    l.getMinDose(),
                    l.getMaxDose(),
                    l.getUnit(),
                    false))
        .toList();
  }

  @Override
  public Optional<DrugMatch> findBrand(UUID brandId) {
    if (brandId == null) {
      return Optional.empty();
    }
    Snapshot s = snapshot();
    DrugBrand b = s.brandById.get(brandId);
    return b == null
        ? Optional.empty()
        : Optional.of(toMatch(b, s, DrugMatch.MatchType.EXACT_BRAND, 1.0));
  }

  private DrugMatch toMatch(DrugBrand b, Snapshot s, DrugMatch.MatchType type, double score) {
    DrugGeneric g = s.genericById.get(b.getGenericId());
    return new DrugMatch(
        b.getId(),
        b.getName(),
        b.getStrength(),
        b.getForm(),
        b.getDgdaRegNo(),
        b.getGenericId(),
        g == null ? null : g.getName(),
        g == null ? null : g.getAtcCode(),
        g == null ? null : g.getRxnormCode(),
        type,
        score,
        false);
  }

  private GenericRef toRef(DrugGeneric g) {
    return new GenericRef(g.getId(), g.getName(), g.getAtcCode(), g.getRxnormCode(), false);
  }

  private static InteractionRule toRule(DrugInteraction i) {
    ConflictSeverity severity = i.getSeverity() == null ? ConflictSeverity.WARNING : i.getSeverity();
    return new InteractionRule(
        i.getGenericAId(), i.getGenericBId(), severity, i.getDescription(), false);
  }

  private static String norm(String s) {
    return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  private record Snapshot(
      Map<UUID, DrugGeneric> genericById,
      Map<String, DrugGeneric> genericByName,
      Map<UUID, DrugBrand> brandById,
      Map<String, List<DrugBrand>> brandsByName,
      List<DrugInteraction> interactions,
      Map<UUID, List<DrugDoseLimit>> limitsByGeneric) {}
}
