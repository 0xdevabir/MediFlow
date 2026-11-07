package com.mediflow.clinical.vitals.service;

import com.mediflow.clinical.vitals.domain.Population;
import com.mediflow.clinical.vitals.domain.VitalRuleSet;
import com.mediflow.clinical.vitals.domain.VitalRuleSpec;
import com.mediflow.clinical.vitals.domain.VitalRuleTable;
import com.mediflow.clinical.vitals.repo.ClinicalVitalRuleRepository;
import com.mediflow.shared.domain.VitalRule;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Loads {@code vital_rule} into an immutable table and caches it in Redis when available. Admin
 * edits (T11) call {@link #evict()} so the next read reloads from Postgres. When Redis is disabled
 * (test profile), an in-process reference is used instead.
 */
@Service
public class VitalRuleCache {

  public static final String REDIS_KEY = "mediflow:vital-rules:v1";

  private static final Logger log = LoggerFactory.getLogger(VitalRuleCache.class);

  private final ClinicalVitalRuleRepository rules;
  private final ObjectProvider<StringRedisTemplate> redis;
  private final AtomicReference<VitalRuleTable> local = new AtomicReference<>();

  public VitalRuleCache(
      ClinicalVitalRuleRepository rules, ObjectProvider<StringRedisTemplate> redis) {
    this.rules = rules;
    this.redis = redis;
  }

  public VitalRuleTable table() {
    VitalRuleTable cached = readRedis();
    if (cached != null) {
      return cached;
    }
    VitalRuleTable localHit = local.get();
    if (localHit != null) {
      return localHit;
    }
    return reload();
  }

  public VitalRuleSet forPopulation(Population population) {
    return table().forPopulation(population);
  }

  public synchronized VitalRuleTable reload() {
    List<VitalRuleSpec> specs = new ArrayList<>();
    for (VitalRule row : rules.findByActiveTrue()) {
      specs.add(toSpec(row));
    }
    VitalRuleTable table = new VitalRuleTable(specs);
    local.set(table);
    writeRedis(table);
    log.info("Vital rule cache loaded size={}", table.size());
    return table;
  }

  /** Called by T11 after a rule edit (or from clinical admin hooks). */
  public void evict() {
    local.set(null);
    StringRedisTemplate template = redis.getIfAvailable();
    if (template != null) {
      try {
        template.delete(REDIS_KEY);
      } catch (RuntimeException e) {
        log.warn("Vital rule Redis evict failed: {}", e.getMessage());
      }
    }
  }

  private VitalRuleTable readRedis() {
    StringRedisTemplate template = redis.getIfAvailable();
    if (template == null) {
      return null;
    }
    try {
      String payload = template.opsForValue().get(REDIS_KEY);
      if (payload == null || payload.isBlank()) {
        return null;
      }
      byte[] bytes = Base64.getDecoder().decode(payload);
      try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
        Object obj = in.readObject();
        if (obj instanceof VitalRuleTable table) {
          local.set(table);
          return table;
        }
      }
    } catch (Exception e) {
      log.warn("Vital rule Redis read failed, falling back to DB: {}", e.getMessage());
    }
    return null;
  }

  private void writeRedis(VitalRuleTable table) {
    StringRedisTemplate template = redis.getIfAvailable();
    if (template == null) {
      return;
    }
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
        out.writeObject(table);
      }
      template.opsForValue().set(REDIS_KEY, Base64.getEncoder().encodeToString(bos.toByteArray()));
    } catch (Exception e) {
      log.warn("Vital rule Redis write failed: {}", e.getMessage());
    }
  }

  static VitalRuleSpec toSpec(VitalRule row) {
    return new VitalRuleSpec(
        row.getVitalType(),
        Population.valueOf(row.getPopulation()),
        row.getUnit(),
        row.getNormalMin(),
        row.getNormalMax(),
        row.getBorderlineMin(),
        row.getBorderlineMax(),
        row.getCriticalMin(),
        row.getCriticalMax());
  }
}
