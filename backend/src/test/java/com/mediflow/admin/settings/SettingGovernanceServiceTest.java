package com.mediflow.admin.settings;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.shared.domain.SettingChangeRequest;
import com.mediflow.shared.domain.SettingChangeStatus;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.SettingChangeRequestRepository;
import com.mediflow.shared.web.CurrentUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class SettingGovernanceServiceTest {

  @Mock SettingChangeRequestRepository requests;
  @Mock SystemSettings settings;
  @Mock CurrentUser currentUser;
  @Mock ClockPort clock;
  @Mock AuditPort audit;
  @Mock JdbcTemplate jdbc;

  SettingGovernanceService service;
  UUID adminId = UUID.randomUUID();
  UUID approverId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new SettingGovernanceService(
            requests, settings, currentUser, clock, audit, jdbc, new ObjectMapper());
    when(clock.now()).thenReturn(Instant.parse("2026-11-07T12:00:00Z"));
  }

  @Test
  void loweringThresholdIsRejectedAtRequest() {
    when(currentUser.requireUserId()).thenReturn(adminId);
    when(settings.currentDecimal(GovernedKeys.AI_CONFIDENCE_THRESHOLD))
        .thenReturn(Optional.of(new BigDecimal("0.85")));

    assertThatThrownBy(() -> service.requestChange(GovernedKeys.AI_CONFIDENCE_THRESHOLD, "0.80"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("admin.settings.threshold.raiseOnly");
    verify(requests, never()).save(any());
  }

  @Test
  void singlePersonCannotApproveOwnRequest() {
    SettingChangeRequest pending = pending(adminId, "0.90");
    when(requests.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(currentUser.requireUserId()).thenReturn(adminId);
    when(settings.currentDecimal(GovernedKeys.AI_CONFIDENCE_THRESHOLD))
        .thenReturn(Optional.of(new BigDecimal("0.85")));

    assertThatThrownBy(() -> service.approve(pending.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("admin.settings.selfApprove");
  }

  @Test
  void dualRoleApprovalAppliesRaise() {
    SettingChangeRequest pending = pending(adminId, "0.90");
    when(requests.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(currentUser.requireUserId()).thenReturn(approverId);
    when(settings.currentDecimal(GovernedKeys.AI_CONFIDENCE_THRESHOLD))
        .thenReturn(Optional.of(new BigDecimal("0.85")));
    when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(adminId), eq("ROLE_ADMIN")))
        .thenReturn(1);
    when(jdbc.queryForObject(
            anyString(), eq(Integer.class), eq(adminId), eq("ROLE_CLINICAL_APPROVER")))
        .thenReturn(0);
    when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(approverId), eq("ROLE_ADMIN")))
        .thenReturn(0);
    when(jdbc.queryForObject(
            anyString(), eq(Integer.class), eq(approverId), eq("ROLE_CLINICAL_APPROVER")))
        .thenReturn(1);
    when(settings.appendVersion(eq(GovernedKeys.AI_CONFIDENCE_THRESHOLD), eq("0.90"), any()))
        .thenReturn(2);
    when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.approve(pending.getId());

    verify(settings).appendVersion(GovernedKeys.AI_CONFIDENCE_THRESHOLD, "0.90", null);
    verify(audit)
        .record(
            eq(approverId),
            eq(null),
            eq("SETTING_CHANGE_APPROVED"),
            eq("SettingChangeRequest"),
            eq(pending.getId()),
            any(Map.class));
  }

  @Test
  void directWriteBlockedForGovernedKey() {
    assertThatThrownBy(
            () -> service.setDirect(GovernedKeys.AI_CONFIDENCE_THRESHOLD, "0.90"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("admin.settings.requiresApproval");
  }

  private SettingChangeRequest pending(UUID requester, String value) {
    SettingChangeRequest row = new SettingChangeRequest();
    row.setId(UUID.randomUUID());
    row.setSettingKey(GovernedKeys.AI_CONFIDENCE_THRESHOLD);
    row.setProposedValue(value);
    row.setRequestedBy(requester);
    row.setStatus(SettingChangeStatus.PENDING);
    row.setRequestedAt(Instant.parse("2026-11-07T11:00:00Z"));
    return row;
  }
}
