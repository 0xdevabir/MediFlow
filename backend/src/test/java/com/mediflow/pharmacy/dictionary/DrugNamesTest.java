package com.mediflow.pharmacy.dictionary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DrugNamesTest {

  @Test
  void normalizeLowercasesAndCollapsesSpace() {
    assertEquals("napa", DrugNames.normalize("  NaPa  "));
    assertEquals("", DrugNames.normalize("   "));
    assertEquals("", DrugNames.normalize(null));
  }

  @Test
  void similarityIsOneForIdenticalNames() {
    assertEquals(1.0, DrugNames.similarity("Paracetamol", "paracetamol"), 1e-9);
  }

  @Test
  void similarityIsLowForUnrelatedNames() {
    assertTrue(DrugNames.similarity("Napa", "Ciprocin") < 0.3);
  }
}
