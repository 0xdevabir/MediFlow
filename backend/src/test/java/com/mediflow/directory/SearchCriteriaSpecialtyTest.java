package com.mediflow.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mediflow.directory.api.DirectoryViews.SearchCriteria;
import org.junit.jupiter.api.Test;

class SearchCriteriaSpecialtyTest {

  @Test
  void normalizesBlankSpecialtyToNull() {
    SearchCriteria blank = new SearchCriteria(null, null, null, "  ", null);
    assertNull(blank.normalizedSpecialty());
  }

  @Test
  void trimsSpecialty() {
    SearchCriteria criteria = new SearchCriteria("cardio", null, "Medicine", " Cardiology ", null);
    assertEquals("Cardiology", criteria.normalizedSpecialty());
    assertEquals("cardio", criteria.normalizedQuery());
    assertEquals("Medicine", criteria.normalizedDepartment());
  }
}
