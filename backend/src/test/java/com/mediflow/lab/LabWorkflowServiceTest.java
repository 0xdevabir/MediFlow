package com.mediflow.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.lab.domain.LabCriticalTask;
import com.mediflow.lab.domain.LabOrderDetail;
import com.mediflow.lab.repo.LabCriticalTaskRepository;
import com.mediflow.lab.repo.LabOrderDetailRepository;
import com.mediflow.lab.repo.LabOrderQueries;
import com.mediflow.lab.repo.LabPackageQueries;
import com.mediflow.lab.repo.LabPatientQueries;
import com.mediflow.lab.repo.LabReferenceRangeRepository;
import com.mediflow.lab.repo.LabReportQueries;
import com.mediflow.lab.repo.LabResultValueRepository;
import com.mediflow.lab.repo.LabSampleQueries;
import com.mediflow.lab.repo.LabStaffQueries;
import com.mediflow.lab.service.LabAccess;
import com.mediflow.lab.service.LabRuleException;
import com.mediflow.lab.service.LabWorkflowService;
import com.mediflow.shared.domain.DiagnosticReport;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.OrderType;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import com.mediflow.shared.port.ObjectStoragePort;
import com.mediflow.shared.repo.DiagnosticReportRepository;
import com.mediflow.shared.repo.LabOrderRepository;
import com.mediflow.shared.repo.SampleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LabWorkflowServiceTest {

  @Mock LabOrderRepository orders;
  @Mock LabOrderQueries orderQueries;
  @Mock LabOrderDetailRepository details;
  @Mock SampleRepository samples;
  @Mock LabSampleQueries sampleQueries;
  @Mock DiagnosticReportRepository reports;
  @Mock LabReportQueries reportQueries;
  @Mock LabResultValueRepository resultValues;
  @Mock LabReferenceRangeRepository ranges;
  @Mock LabCriticalTaskRepository criticalTasks;
  @Mock LabPackageQueries packages;
  @Mock LabPatientQueries patients;
  @Mock LabStaffQueries staff;
  @Mock LabAccess access;
  @Mock ObjectStoragePort storage;
  @Mock NotificationPort notifications;
  @Mock ApplicationEventPublisher events;
  @Mock AuditPort audit;
  @Mock ClockPort clock;

  LabWorkflowService service;

  @BeforeEach
  void setUp() {
    service =
        new LabWorkflowService(
            orders,
            orderQueries,
            details,
            samples,
            sampleQueries,
            reports,
            reportQueries,
            resultValues,
            ranges,
            criticalTasks,
            packages,
            patients,
            staff,
            access,
            storage,
            notifications,
            events,
            audit,
            clock);
  }

  @Test
  void verifyRejectsSelfVerification() {
    UUID orderId = UUID.randomUUID();
    UUID tech = UUID.randomUUID();
    LabOrder order = order(orderId, LabOrderStatus.RESULTED, OrderType.DIRECT);
    LabOrderDetail detail = detail(orderId, tech);

    when(orderQueries.findById(orderId)).thenReturn(Optional.of(order));
    when(details.findByLabOrderId(orderId)).thenReturn(Optional.of(detail));
    when(access.requireUserId()).thenReturn(tech);
    when(access.isAdmin()).thenReturn(false);

    assertThatThrownBy(() -> service.verify(orderId))
        .isInstanceOf(LabRuleException.class)
        .extracting(ex -> ((LabRuleException) ex).getMessageKey())
        .isEqualTo("lab.error.selfVerify");
  }

  @Test
  void criticalVerifyCreatesPhoneTaskAndAlertsDutyDoctors() {
    UUID orderId = UUID.randomUUID();
    UUID hospitalId = UUID.randomUUID();
    UUID uploader = UUID.randomUUID();
    UUID verifier = UUID.randomUUID();
    UUID duty = UUID.randomUUID();
    UUID reportId = UUID.randomUUID();

    LabOrder order = order(orderId, LabOrderStatus.RESULTED, OrderType.DIRECT);
    order.setHospitalId(hospitalId);
    LabOrderDetail detail = detail(orderId, uploader);

    DiagnosticReport report = new DiagnosticReport();
    report.setId(reportId);
    report.setLabOrderId(orderId);
    report.setAbnormalFlag(true);
    report.setCriticalFlag(true);

    when(orderQueries.findById(orderId)).thenReturn(Optional.of(order));
    when(details.findByLabOrderId(orderId)).thenReturn(Optional.of(detail));
    when(access.requireUserId()).thenReturn(verifier);
    when(access.isAdmin()).thenReturn(false);
    when(reportQueries.findByLabOrderId(orderId)).thenReturn(Optional.of(report));
    when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(orders.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(criticalTasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(clock.now()).thenReturn(Instant.parse("2026-11-07T12:00:00Z"));
    when(staff.dutyDoctorUserIds(hospitalId)).thenReturn(List.of(duty));

    service.verify(orderId);

    ArgumentCaptor<LabCriticalTask> taskCap = ArgumentCaptor.forClass(LabCriticalTask.class);
    verify(criticalTasks).save(taskCap.capture());
    assertThat(taskCap.getValue().getStatus()).isEqualTo(LabCriticalTask.Status.OPEN);
    assertThat(taskCap.getValue().getAssigneeId()).isEqualTo(verifier);

    verify(notifications)
        .notifyUser(eq(duty), eq("IN_APP"), eq("en"), eq("Critical lab value"), any());

    ArgumentCaptor<Object> eventCap = ArgumentCaptor.forClass(Object.class);
    verify(events).publishEvent(eventCap.capture());
    assertThat(eventCap.getValue()).isInstanceOf(DomainEvents.LabReportVerified.class);
    DomainEvents.LabReportVerified ev = (DomainEvents.LabReportVerified) eventCap.getValue();
    assertThat(ev.critical()).isTrue();
    assertThat(ev.abnormal()).isTrue();
  }

  @Test
  void nonCriticalVerifyDoesNotCreatePhoneTask() {
    UUID orderId = UUID.randomUUID();
    UUID uploader = UUID.randomUUID();
    UUID verifier = UUID.randomUUID();

    LabOrder order = order(orderId, LabOrderStatus.RESULTED, OrderType.DIRECT);
    LabOrderDetail detail = detail(orderId, uploader);
    DiagnosticReport report = new DiagnosticReport();
    report.setId(UUID.randomUUID());
    report.setLabOrderId(orderId);
    report.setAbnormalFlag(true);
    report.setCriticalFlag(false);

    when(orderQueries.findById(orderId)).thenReturn(Optional.of(order));
    when(details.findByLabOrderId(orderId)).thenReturn(Optional.of(detail));
    when(access.requireUserId()).thenReturn(verifier);
    when(access.isAdmin()).thenReturn(false);
    when(reportQueries.findByLabOrderId(orderId)).thenReturn(Optional.of(report));
    when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(orders.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(clock.now()).thenReturn(Instant.parse("2026-11-07T12:00:00Z"));

    service.verify(orderId);

    verify(criticalTasks, never()).save(any());
    verify(staff, never()).dutyDoctorUserIds(any());
  }

  private static LabOrder order(UUID id, LabOrderStatus status, OrderType type) {
    LabOrder o = new LabOrder();
    o.setId(id);
    o.setPatientId(UUID.randomUUID());
    o.setHospitalId(UUID.randomUUID());
    o.setStatus(status);
    o.setOrderType(type);
    o.setOrderedAt(Instant.parse("2026-11-07T08:00:00Z"));
    return o;
  }

  private static LabOrderDetail detail(UUID orderId, UUID resultedBy) {
    LabOrderDetail d = new LabOrderDetail();
    d.setLabOrderId(orderId);
    d.setResultedBy(resultedBy);
    d.setQrToken("token");
    d.setBookingRef("LB-X");
    return d;
  }
}
