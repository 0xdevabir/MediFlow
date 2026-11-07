package com.mediflow.prescription.safety;

import com.mediflow.shared.repo.DrugBrandRepository;
import com.mediflow.shared.repo.DrugDoseLimitRepository;
import com.mediflow.shared.repo.DrugGenericRepository;
import com.mediflow.shared.repo.DrugInteractionRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default dictionary: reads the shared drug_brand / drug_generic / drug_interaction /
 * drug_dose_limit tables (seeded by T08) into an in-memory snapshot refreshed every 60 s.
 */
@Component
public class RepositoryRxDrugDictionary implements RxDrugDictionary {

  private static final Duration TTL = Duration.ofSeconds(60);

  private final DrugGenericRepository generics;
  private final DrugBrandRepository brands;
  private final DrugInteractionRepository interactions;
  private final DrugDoseLimitRepository limits;

  private volatile DictionarySnapshot snapshot;
  private volatile long loadedAtNanos;

  public RepositoryRxDrugDictionary(
      DrugGenericRepository generics,
      DrugBrandRepository brands,
      DrugInteractionRepository interactions,
      DrugDoseLimitRepository limits) {
    this.generics = generics;
    this.brands = brands;
    this.interactions = interactions;
    this.limits = limits;
  }

  /** Current snapshot (reloaded when older than the TTL). */
  @Transactional(readOnly = true)
  public DictionarySnapshot snapshot() {
    DictionarySnapshot s = snapshot;
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

  /** Forces a reload on next access (e.g. after T08 imports a new DGDA list). */
  public void invalidate() {
    snapshot = null;
  }

  private DictionarySnapshot load() {
    return new DictionarySnapshot(
        generics.findAll().stream()
            .map(g -> new GenericInfo(g.getId(), g.getName(), g.getAtcCode()))
            .toList(),
        brands.findAll().stream()
            .map(
                b ->
                    new BrandInfo(
                        b.getId(),
                        b.getName(),
                        b.getGenericId(),
                        b.getStrength(),
                        b.getForm(),
                        b.getDgdaRegNo()))
            .toList(),
        interactions.findAll().stream()
            .map(
                i ->
                    new InteractionInfo(
                        i.getGenericAId(), i.getGenericBId(), i.getSeverity(), i.getDescription()))
            .toList(),
        limits.findAll().stream()
            .map(
                l ->
                    new DoseLimitInfo(
                        l.getGenericId(),
                        l.getPopulation(),
                        l.getRoute(),
                        l.getMinDose(),
                        l.getMaxDose(),
                        l.getUnit()))
            .toList());
  }

  @Override
  public Optional<GenericInfo> generic(UUID genericId) {
    return snapshot().generic(genericId);
  }

  @Override
  public Optional<BrandInfo> brand(UUID brandId) {
    return snapshot().brand(brandId);
  }

  @Override
  public List<BrandInfo> brandsByName(String name) {
    return snapshot().brandsByName(name);
  }

  @Override
  public Optional<GenericInfo> genericByName(String name) {
    return snapshot().genericByName(name);
  }

  @Override
  public Optional<InteractionInfo> interaction(UUID genericA, UUID genericB) {
    return snapshot().interaction(genericA, genericB);
  }

  @Override
  public List<DoseLimitInfo> doseLimits(UUID genericId) {
    return snapshot().doseLimits(genericId);
  }

  @Override
  public List<BrandInfo> searchBrands(String query, int limit) {
    return snapshot().searchBrands(query, limit);
  }
}
