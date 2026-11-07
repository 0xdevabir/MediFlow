package com.mediflow.pharmacy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Module-owned settings (see {@code application-pharmacy.yml} / {@code MEDIFLOW_*} env vars). */
@ConfigurationProperties(prefix = "mediflow.pharmacy")
public class PharmacyProperties {

  /** Shared secret for {@code GET /api/internal/dictionary/export} (T09). Empty → endpoint denies. */
  private String internalApiKey = "";

  /** Max CSV rows accepted in one DGDA import (header excluded). */
  private int importMaxRows = 50_000;

  public String getInternalApiKey() {
    return internalApiKey;
  }

  public void setInternalApiKey(String internalApiKey) {
    this.internalApiKey = internalApiKey == null ? "" : internalApiKey;
  }

  public int getImportMaxRows() {
    return importMaxRows;
  }

  public void setImportMaxRows(int importMaxRows) {
    this.importMaxRows = importMaxRows;
  }
}
