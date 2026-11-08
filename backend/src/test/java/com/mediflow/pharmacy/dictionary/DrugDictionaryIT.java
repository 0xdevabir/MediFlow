package com.mediflow.pharmacy.dictionary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediflow.shared.port.DrugDictionaryPort;
import com.mediflow.shared.port.DrugDictionaryPort.GenericRef;
import com.mediflow.support.AbstractIntegrationTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * Acceptance: two brands of the same generic resolve to that generic; unknown brand → empty (never
 * guessed). Bean under test is {@link DrugDictionaryService} for pharmacy helpers and shared port.
 */
@ActiveProfiles({"test", "dev"})
class DrugDictionaryIT extends AbstractIntegrationTest {

  @Autowired DrugDictionaryService dictionary;
  @Autowired DrugDictionaryPort sharedPort;

  @Test
  void napaAndAceResolveToParacetamol() {
    Optional<GenericRef> napa = dictionary.resolveGeneric("Napa");
    Optional<GenericRef> ace = dictionary.resolveGeneric("Ace");
    assertTrue(napa.isPresent());
    assertTrue(ace.isPresent());
    assertEquals("Paracetamol", napa.get().name());
    assertEquals(napa.get().id(), ace.get().id());
    assertEquals("N02BE01", napa.get().atcCode());
  }

  @Test
  void secloAndCiprocinMapToExpectedGenerics() {
    assertEquals("Omeprazole", dictionary.resolveGeneric("Seclo").orElseThrow().name());
    assertEquals("Ciprofloxacin", dictionary.resolveGeneric("Ciprocin").orElseThrow().name());
  }

  @Test
  void unknownBrandIsEmptyNeverGuessed() {
    assertTrue(dictionary.resolveGeneric("TotallyUnknownBrandXYZ").isEmpty());
    assertTrue(dictionary.findBrandProducts("TotallyUnknownBrandXYZ").isEmpty());
  }

  @Test
  void brandsOfSameGenericIncludeBothTradeNames() {
    UUID genericId = dictionary.resolveGeneric("Napa").orElseThrow().id();
    var brands = dictionary.brandsOfGeneric(genericId);
    assertTrue(brands.stream().anyMatch(b -> "Napa".equalsIgnoreCase(b.brandName())));
    assertTrue(brands.stream().anyMatch(b -> "Ace".equalsIgnoreCase(b.brandName())));
  }

  @Test
  void sharedPortBeanIsDrugDictionaryService() {
    assertTrue(sharedPort instanceof DrugDictionaryService);
    assertEquals(
        dictionary.findBrandProducts("Napa").size(), sharedPort.findBrandProducts("Napa").size());
  }
}
