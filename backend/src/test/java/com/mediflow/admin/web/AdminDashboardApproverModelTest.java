package com.mediflow.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.admin.settings.SettingGovernanceService;
import com.mediflow.audit.service.AuditAnomalyService;
import com.mediflow.audit.service.AuditChainService;
import com.mediflow.shared.domain.SettingChangeRequest;
import com.mediflow.shared.web.CurrentUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/**
 * CLINICAL_APPROVER must see the pending dual-approval queue and must not receive audit anomaly /
 * chain widgets that can surface patient-touch aggregates.
 */
@ExtendWith(MockitoExtension.class)
class AdminDashboardApproverModelTest {

  @Mock AuditChainService chain;
  @Mock AuditAnomalyService anomalies;
  @Mock SettingGovernanceService governance;
  @Mock CurrentUser currentUser;

  AdminDashboardController controller;

  @BeforeEach
  void setUp() {
    controller = new AdminDashboardController(chain, anomalies, governance, currentUser);
  }

  @Test
  void approverSeesPendingWithoutAnomalyOrChain() {
    when(currentUser.hasRole("ADMIN")).thenReturn(false);
    when(currentUser.hasRole("CLINICAL_APPROVER")).thenReturn(true);
    when(governance.pending()).thenReturn(List.of());

    Model model = new ConcurrentModel();
    String view = controller.home(model);

    assertThat(view).isEqualTo("admin/dashboard");
    assertThat(model.getAttribute("isAdmin")).isEqualTo(false);
    assertThat(model.getAttribute("isApprover")).isEqualTo(true);
    assertThat(model.getAttribute("pending")).isEqualTo(List.of());
    assertThat(model.getAttribute("pageTitleKey")).isEqualTo("admin.approver.title");
    assertThat(model.getAttribute("lastCheck")).isNull();
    assertThat(model.getAttribute("openAnomalies")).isEqualTo(0);
    verify(anomalies, never()).list(true);
    verify(chain, never()).lastCheck();
  }

  @Test
  void adminReceivesComplianceWidgets() {
    when(currentUser.hasRole("ADMIN")).thenReturn(true);
    when(currentUser.hasRole("CLINICAL_APPROVER")).thenReturn(false);
    when(governance.pending()).thenReturn(List.of());
    when(chain.lastCheck()).thenReturn(Optional.empty());
    when(anomalies.list(true)).thenReturn(List.of());

    Model model = new ConcurrentModel();
    controller.home(model);

    assertThat(model.getAttribute("isAdmin")).isEqualTo(true);
    assertThat(model.getAttribute("pageTitleKey")).isEqualTo("admin.dashboard.title");
    assertThat(model.getAttribute("openAnomalies")).isEqualTo(0);
    verify(anomalies).list(true);
    verify(chain).lastCheck();
  }
}
