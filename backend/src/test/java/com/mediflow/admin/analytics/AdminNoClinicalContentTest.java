package com.mediflow.admin.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Acceptance: admin analytics must not select clinical free-text / diagnosis / vitals values /
 * prescription raw text.
 */
class AdminNoClinicalContentTest {

  @Test
  void analyticsSqlAvoidsClinicalColumns() throws Exception {
    Path src =
        Path.of("src/main/java/com/mediflow/admin/analytics/AnalyticsService.java");
    if (!Files.exists(src)) {
      src = Path.of("backend/src/main/java/com/mediflow/admin/analytics/AnalyticsService.java");
    }
    String body = Files.readString(src).toLowerCase();
    assertThat(body)
        .doesNotContain("diagnosis_summary")
        .doesNotContain("value_numeric")
        .doesNotContain("value_text")
        .doesNotContain("raw_text")
        .doesNotContain("national_id")
        .doesNotContain("result_json");
  }

  @Test
  void dhis2ExportIsAggregateOnly() throws Exception {
    Path src = Path.of("src/main/java/com/mediflow/admin/analytics/Dhis2ExportService.java");
    if (!Files.exists(src)) {
      src = Path.of("backend/src/main/java/com/mediflow/admin/analytics/Dhis2ExportService.java");
    }
    String body = Files.readString(src);
    assertThat(body).contains("dataValues");
    assertThat(body.toLowerCase())
        .doesNotContain("http://")
        .doesNotContain("https://")
        .doesNotContain("resttemplate")
        .doesNotContain("webclient");
  }
}
