package com.mediflow.prescription.safety;

import com.mediflow.shared.domain.SystemSetting;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-only access to {@code ai.confidence.threshold} (T11 owns writes / raise-only). */
@Component
public class ThresholdSettings {

  public static final String KEY = "ai.confidence.threshold";

  private final EntityManager em;

  public ThresholdSettings(EntityManager em) {
    this.em = em;
  }

  @Transactional(readOnly = true)
  public BigDecimal confidenceThreshold() {
    SystemSetting setting =
        em.createQuery(
                "select s from SystemSetting s where s.settingKey = :key", SystemSetting.class)
            .setParameter("key", KEY)
            .getResultStream()
            .findFirst()
            .orElse(null);
    return ConfidenceGate.parseThreshold(setting == null ? null : setting.getSettingValue());
  }
}
