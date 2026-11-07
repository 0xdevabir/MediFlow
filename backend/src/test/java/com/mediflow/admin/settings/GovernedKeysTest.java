package com.mediflow.admin.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GovernedKeysTest {

  @Test
  void classifiesGovernedAndRaiseOnlyKeys() {
    assertThat(GovernedKeys.requiresDualApproval("ai.confidence.threshold")).isTrue();
    assertThat(GovernedKeys.requiresDualApproval("clinical.anything")).isTrue();
    assertThat(GovernedKeys.requiresDualApproval("vital_rule." + java.util.UUID.randomUUID()))
        .isTrue();
    assertThat(GovernedKeys.requiresDualApproval("audit.retention.years")).isFalse();
    assertThat(GovernedKeys.isRaiseOnly("ai.confidence.threshold")).isTrue();
    assertThat(GovernedKeys.isRaiseOnly("clinical.x")).isFalse();
  }
}
