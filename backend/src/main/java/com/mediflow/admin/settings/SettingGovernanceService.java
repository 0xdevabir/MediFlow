package com.mediflow.admin.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.shared.domain.SettingChangeRequest;
import com.mediflow.shared.domain.SettingChangeStatus;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.SettingChangeRequestRepository;
import com.mediflow.shared.web.CurrentUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Versioned settings with dual approval for clinical / AI threshold changes. Threshold is
 * raise-only; single-person self-approval is rejected.
 */
@Service
public class SettingGovernanceService {

  private final SettingChangeRequestRepository requests;
  private final SystemSettings settings;
  private final CurrentUser currentUser;
  private final ClockPort clock;
  private final AuditPort audit;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public SettingGovernanceService(
      SettingChangeRequestRepository requests,
      SystemSettings settings,
      CurrentUser currentUser,
      ClockPort clock,
      AuditPort audit,
      JdbcTemplate jdbc,
      ObjectMapper mapper) {
    this.requests = requests;
    this.settings = settings;
    this.currentUser = currentUser;
    this.clock = clock;
    this.audit = audit;
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @PreAuthorize("hasAnyRole('ADMIN','CLINICAL_APPROVER')")
  @Transactional(readOnly = true)
  public List<SettingChangeRequest> pending() {
    return requests.findAll().stream()
        .filter(r -> r.getStatus() == SettingChangeStatus.PENDING)
        .sorted((a, b) -> b.getRequestedAt().compareTo(a.getRequestedAt()))
        .toList();
  }

  @PreAuthorize("hasAnyRole('ADMIN','CLINICAL_APPROVER')")
  @Transactional(readOnly = true)
  public List<SystemSettings.SettingVersion> latestSettings() {
    return settings.latestAll();
  }

  /**
   * Admin-only direct write for non-governed keys (e.g. audit retention). Governed keys must go
   * through {@link #requestChange}.
   */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public int setDirect(String key, String value) {
    if (GovernedKeys.requiresDualApproval(key)) {
      throw new IllegalArgumentException("admin.settings.requiresApproval");
    }
    validateRaiseOnly(key, value);
    int ver = settings.appendVersion(key, value, null);
    UUID actor = currentUser.requireUserId();
    audit.record(
        actor,
        null,
        "SETTING_CHANGED",
        "SystemSetting",
        null,
        Map.of("key", key, "value", value, "version", ver));
    return ver;
  }

  @PreAuthorize("hasAnyRole('ADMIN','CLINICAL_APPROVER')")
  @Transactional
  public SettingChangeRequest requestChange(String key, String value) {
    if (key == null || key.isBlank() || value == null || value.isBlank()) {
      throw new IllegalArgumentException("admin.settings.invalid");
    }
    String trimmedKey = key.trim();
    String trimmedValue = value.trim();
    if (!GovernedKeys.requiresDualApproval(trimmedKey)) {
      throw new IllegalArgumentException("admin.settings.notGoverned");
    }
    validateRaiseOnly(trimmedKey, trimmedValue);

    SettingChangeRequest row = new SettingChangeRequest();
    row.setSettingKey(trimmedKey);
    row.setProposedValue(trimmedValue);
    row.setRequestedBy(currentUser.requireUserId());
    row.setStatus(SettingChangeStatus.PENDING);
    row.setRequestedAt(clock.now());
    SettingChangeRequest saved = requests.save(row);
    audit.record(
        currentUser.requireUserId(),
        null,
        "SETTING_CHANGE_REQUESTED",
        "SettingChangeRequest",
        saved.getId(),
        Map.of("key", trimmedKey, "value", trimmedValue));
    return saved;
  }

  @PreAuthorize("hasAnyRole('ADMIN','CLINICAL_APPROVER')")
  @Transactional
  public SettingChangeRequest approve(UUID requestId) {
    SettingChangeRequest row =
        requests
            .findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("admin.settings.request.notFound"));
    if (row.getStatus() != SettingChangeStatus.PENDING) {
      throw new IllegalArgumentException("admin.settings.request.notPending");
    }
    UUID actor = currentUser.requireUserId();
    if (actor.equals(row.getRequestedBy())) {
      throw new IllegalArgumentException("admin.settings.selfApprove");
    }
    requireComplementaryRole(row.getRequestedBy(), actor);

    validateRaiseOnly(row.getSettingKey(), row.getProposedValue());
    apply(row.getSettingKey(), row.getProposedValue());

    row.setStatus(SettingChangeStatus.APPROVED);
    row.setApprovedBy(actor);
    row.setDecidedAt(clock.now());
    SettingChangeRequest saved = requests.save(row);
    audit.record(
        actor,
        null,
        "SETTING_CHANGE_APPROVED",
        "SettingChangeRequest",
        saved.getId(),
        Map.of("key", saved.getSettingKey(), "value", saved.getProposedValue()));
    return saved;
  }

  @PreAuthorize("hasAnyRole('ADMIN','CLINICAL_APPROVER')")
  @Transactional
  public SettingChangeRequest reject(UUID requestId, String reason) {
    SettingChangeRequest row =
        requests
            .findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("admin.settings.request.notFound"));
    if (row.getStatus() != SettingChangeStatus.PENDING) {
      throw new IllegalArgumentException("admin.settings.request.notPending");
    }
    UUID actor = currentUser.requireUserId();
    if (actor.equals(row.getRequestedBy())) {
      throw new IllegalArgumentException("admin.settings.selfReject");
    }
    requireComplementaryRole(row.getRequestedBy(), actor);

    row.setStatus(SettingChangeStatus.REJECTED);
    row.setApprovedBy(actor);
    row.setDecidedAt(clock.now());
    SettingChangeRequest saved = requests.save(row);
    audit.record(
        actor,
        null,
        "SETTING_CHANGE_REJECTED",
        "SettingChangeRequest",
        saved.getId(),
        Map.of("key", saved.getSettingKey(), "reason", reason == null ? "" : reason));
    return saved;
  }

  void validateRaiseOnly(String key, String proposed) {
    if (!GovernedKeys.isRaiseOnly(key)) {
      return;
    }
    BigDecimal next;
    try {
      next = new BigDecimal(proposed.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("admin.settings.threshold.invalid");
    }
    if (next.compareTo(BigDecimal.ZERO) < 0 || next.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("admin.settings.threshold.range");
    }
    BigDecimal current =
        settings.currentDecimal(key).orElse(new BigDecimal("0.85"));
    if (next.compareTo(current) < 0) {
      throw new IllegalArgumentException("admin.settings.threshold.raiseOnly");
    }
  }

  /**
   * Dual approval needs ADMIN and CLINICAL_APPROVER represented across the two different users
   * (requester + current approver). One person holding both roles cannot self-approve.
   */
  private void requireComplementaryRole(UUID requesterId, UUID approverId) {
    boolean coversAdmin =
        hasRole(requesterId, "ROLE_ADMIN") || hasRole(approverId, "ROLE_ADMIN");
    boolean coversApprover =
        hasRole(requesterId, "ROLE_CLINICAL_APPROVER")
            || hasRole(approverId, "ROLE_CLINICAL_APPROVER");
    if (!coversAdmin || !coversApprover) {
      throw new AccessDeniedException("admin.settings.dualRoleRequired");
    }
  }

  private boolean hasRole(UUID userId, String role) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE user_id = ? AND role = ?",
            Integer.class,
            userId,
            role);
    return n != null && n > 0;
  }

  private void apply(String key, String value) {
    if (key.startsWith("vital_rule.")) {
      applyVitalRule(key.substring("vital_rule.".length()), value);
    } else if (key.startsWith("drug_interaction.")) {
      applyDrugInteraction(key.substring("drug_interaction.".length()), value);
    } else if (key.startsWith("clinical.")) {
      settings.appendVersion(key, value, "Approved clinical setting change");
    } else {
      settings.appendVersion(key, value, null);
    }
  }

  private void applyVitalRule(String idText, String json) {
    UUID id = UUID.fromString(idText);
    try {
      JsonNode n = mapper.readTree(json);
      jdbc.update(
          """
          UPDATE vital_rule SET
            normal_min = COALESCE(?, normal_min),
            normal_max = COALESCE(?, normal_max),
            borderline_min = COALESCE(?, borderline_min),
            borderline_max = COALESCE(?, borderline_max),
            critical_min = COALESCE(?, critical_min),
            critical_max = COALESCE(?, critical_max),
            active = COALESCE(?, active),
            updated_at = now(),
            version = version + 1
          WHERE id = ?
          """,
          decimal(n, "normalMin"),
          decimal(n, "normalMax"),
          decimal(n, "borderlineMin"),
          decimal(n, "borderlineMax"),
          decimal(n, "criticalMin"),
          decimal(n, "criticalMax"),
          n.has("active") ? n.get("active").asBoolean() : null,
          id);
      settings.appendVersion("vital_rule." + id, json, "Applied vital_rule change");
    } catch (Exception e) {
      throw new IllegalArgumentException("admin.settings.vitalRule.invalid");
    }
  }

  private void applyDrugInteraction(String idText, String json) {
    UUID id = UUID.fromString(idText);
    try {
      JsonNode n = mapper.readTree(json);
      jdbc.update(
          """
          UPDATE drug_interaction SET
            severity = COALESCE(?, severity),
            description = COALESCE(?, description),
            updated_at = now(),
            version = version + 1
          WHERE id = ?
          """,
          text(n, "severity"),
          text(n, "description"),
          id);
      settings.appendVersion("drug_interaction." + id, json, "Applied drug_interaction change");
    } catch (Exception e) {
      throw new IllegalArgumentException("admin.settings.interaction.invalid");
    }
  }

  private static BigDecimal decimal(JsonNode n, String field) {
    if (!n.has(field) || n.get(field).isNull()) {
      return null;
    }
    return n.get(field).decimalValue();
  }

  private static String text(JsonNode n, String field) {
    if (!n.has(field) || n.get(field).isNull()) {
      return null;
    }
    return n.get(field).asText();
  }
}
