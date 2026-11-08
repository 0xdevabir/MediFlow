package com.mediflow.prescription.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediflow.shared.domain.ConflictSeverity;
import com.mediflow.shared.port.DrugDictionaryPort;
import com.mediflow.shared.repo.DrugBrandRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** Safety checks resolve drugs via {@link DrugDictionaryPort}. */
class PortRxDrugDictionaryTest {

  private static final UUID GENERIC = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID BRAND = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

  private PortRxDrugDictionary adapter;

  @BeforeEach
  void setUp() {
    DrugDictionaryPort port = new FakePort();
    ObjectProvider<RepositoryDrugDictionaryPort> none =
        new ObjectProvider<>() {
          @Override
          public RepositoryDrugDictionaryPort getObject() {
            return null;
          }
        };
    adapter = new PortRxDrugDictionary(port, (DrugBrandRepository) null, none);
  }

  @Test
  void brandsByNameUsesExactPortHits() {
    List<RxDrugDictionary.BrandInfo> hits = adapter.brandsByName("Moxacil");
    assertEquals(1, hits.size());
    assertEquals(BRAND, hits.get(0).id());
    assertEquals(GENERIC, hits.get(0).genericId());
  }

  @Test
  void interactionAndDoseLimitsMapped() {
    assertTrue(adapter.interaction(GENERIC, GENERIC).isPresent());
    assertEquals(ConflictSeverity.CRITICAL, adapter.interaction(GENERIC, GENERIC).get().severity());
    assertEquals(1, adapter.doseLimits(GENERIC).size());
  }

  @Test
  void genericLookup() {
    assertEquals("Amoxicillin", adapter.generic(GENERIC).orElseThrow().name());
  }

  private static final class FakePort implements DrugDictionaryPort {
    @Override
    public List<DrugMatch> findBrandProducts(String brandName) {
      if ("Moxacil".equalsIgnoreCase(brandName)) {
        return List.of(
            new DrugMatch(
                BRAND,
                "Moxacil",
                "500 mg",
                "capsule",
                "DGDA-1",
                GENERIC,
                "Amoxicillin",
                "J01CA04",
                null,
                DrugMatch.MatchType.EXACT_BRAND,
                1.0,
                false));
      }
      return List.of();
    }

    @Override
    public List<DrugMatch> suggestBrands(String query) {
      return findBrandProducts(query);
    }

    @Override
    public Optional<GenericRef> findGeneric(UUID genericId) {
      return GENERIC.equals(genericId)
          ? Optional.of(new GenericRef(GENERIC, "Amoxicillin", "J01CA04", null, false))
          : Optional.empty();
    }

    @Override
    public Optional<GenericRef> findGenericByName(String name) {
      return "Amoxicillin".equalsIgnoreCase(name) ? findGeneric(GENERIC) : Optional.empty();
    }

    @Override
    public List<InteractionRule> interactionsAmong(Collection<UUID> genericIds) {
      return List.of();
    }

    @Override
    public Optional<InteractionRule> interaction(UUID genericA, UUID genericB) {
      return Optional.of(
          new InteractionRule(genericA, genericB, ConflictSeverity.CRITICAL, "self", false));
    }

    @Override
    public List<DoseLimitRule> doseLimits(UUID genericId) {
      return List.of(
          new DoseLimitRule(
              genericId, "ADULT", "oral", new BigDecimal("250"), new BigDecimal("1000"), "mg", false));
    }

    @Override
    public Optional<DrugMatch> findBrand(UUID brandId) {
      return BRAND.equals(brandId)
          ? findBrandProducts("Moxacil").stream().findFirst()
          : Optional.empty();
    }
  }
}
