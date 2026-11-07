package com.mediflow.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mediflow.lab.domain.LabOrderDetail;
import com.mediflow.lab.domain.LabResultValue;
import com.mediflow.lab.domain.ResultFlag;
import com.mediflow.lab.repo.LabCatalogQueries;
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
import com.mediflow.lab.service.LabQueryService;
import com.mediflow.lab.service.LabViews;
import com.mediflow.shared.domain.DiagnosticReport;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.LabTest;
import com.mediflow.shared.domain.OrderType;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.port.ObjectStoragePort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabReleaseRulesTest {

  @Mock LabOrderQueries orders;
  @Mock LabOrderDetailRepository details;
  @Mock LabReportQueries reports;
  @Mock LabResultValueRepository resultValues;
  @Mock LabReferenceRangeRepository ranges;
  @Mock LabSampleQueries samples;
  @Mock LabCatalogQueries tests;
  @Mock LabPackageQueries packages;
  @Mock LabPatientQueries patients;
  @Mock LabCriticalTaskRepository criticalTasks;
  @Mock LabStaffQueries staff;
  @Mock LabAccess access;
  @Mock ObjectStoragePort storage;

  LabQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new LabQueryService(
            orders,
            details,
            reports,
            resultValues,
            ranges,
            samples,
            tests,
            packages,
            patients,
            criticalTasks,
            staff,
            access,
            storage);
  }

  @Test
  void patientCannotSeeUnverifiedResults() {
    UUID orderId = UUID.randomUUID();
    UUID patientId = UUID.randomUUID();
    UUID testId = UUID.randomUUID();

    LabOrder order = new LabOrder();
    order.setId(orderId);
    order.setPatientId(patientId);
    order.setHospitalId(UUID.randomUUID());
    order.setTestId(testId);
    order.setOrderType(OrderType.DIRECT);
    order.setStatus(LabOrderStatus.RESULTED);
    order.setOrderedAt(Instant.parse("2026-11-07T09:00:00Z"));

    LabOrderDetail detail = new LabOrderDetail();
    detail.setLabOrderId(orderId);
    detail.setBookingRef("LB-TEST");
    detail.setPrice(new BigDecimal("150"));

    DiagnosticReport report = new DiagnosticReport();
    report.setId(UUID.randomUUID());
    report.setLabOrderId(orderId);
    report.setAbnormalFlag(true);
    report.setCriticalFlag(false);
    report.setSummary("secret");
    report.setFileRef("lab-reports/x.pdf");

    LabResultValue value = new LabResultValue();
    value.setAnalyteCode("GLU");
    value.setAnalyteName("Glucose");
    value.setValueNumeric(new BigDecimal("8.0"));
    value.setFlag(ResultFlag.HIGH);

    LabTest test = new LabTest();
    test.setId(testId);
    test.setName("RBS");
    test.setCode("RBS");
    test.setActive(true);

    Patient patient = new Patient();
    patient.setId(patientId);
    patient.setGivenName("A");
    patient.setFamilyName("B");

    when(orders.findById(orderId)).thenReturn(Optional.of(order));
    when(details.findByLabOrderId(orderId)).thenReturn(Optional.of(detail));
    when(details.findByLabOrderIdIn(any())).thenReturn(List.of(detail));
    when(reports.findByLabOrderId(orderId)).thenReturn(Optional.of(report));
    when(reports.findByLabOrderIdIn(any())).thenReturn(List.of(report));
    when(tests.findByIdIn(any())).thenReturn(List.of(test));
    when(patients.findById(patientId)).thenReturn(Optional.of(patient));
    when(access.currentPatient()).thenReturn(Optional.of(patient));
    when(access.isLabTech()).thenReturn(false);
    when(access.isDoctor()).thenReturn(false);
    when(access.isFrontDesk()).thenReturn(false);
    when(access.isAdmin()).thenReturn(false);
    when(resultValues.findByReportIdOrderBySortOrderAsc(report.getId()))
        .thenReturn(List.of(value));

    LabViews.ReportView view = service.report(orderId);

    assertThat(view.released()).isFalse();
    assertThat(view.values()).isEmpty();
    assertThat(view.summary()).isNull();
    assertThat(view.hasPdf()).isFalse();
    assertThat(view.abnormal()).isFalse();
  }

  @Test
  void patientSeesVerifiedResults() {
    UUID orderId = UUID.randomUUID();
    UUID patientId = UUID.randomUUID();
    UUID testId = UUID.randomUUID();

    LabOrder order = new LabOrder();
    order.setId(orderId);
    order.setPatientId(patientId);
    order.setHospitalId(UUID.randomUUID());
    order.setTestId(testId);
    order.setOrderType(OrderType.DIRECT);
    order.setStatus(LabOrderStatus.VERIFIED);
    order.setOrderedAt(Instant.parse("2026-11-07T09:00:00Z"));

    LabOrderDetail detail = new LabOrderDetail();
    detail.setLabOrderId(orderId);
    detail.setBookingRef("LB-TEST");

    DiagnosticReport report = new DiagnosticReport();
    report.setId(UUID.randomUUID());
    report.setLabOrderId(orderId);
    report.setAbnormalFlag(true);
    report.setCriticalFlag(false);
    report.setSummary("see doctor");
    report.setVerifiedAt(Instant.parse("2026-11-07T12:00:00Z"));

    LabResultValue value = new LabResultValue();
    value.setAnalyteCode("GLU");
    value.setAnalyteName("Glucose");
    value.setValueNumeric(new BigDecimal("8.0"));
    value.setFlag(ResultFlag.HIGH);

    LabTest test = new LabTest();
    test.setId(testId);
    test.setName("RBS");
    test.setCode("RBS");
    test.setActive(true);

    Patient patient = new Patient();
    patient.setId(patientId);
    patient.setGivenName("A");
    patient.setFamilyName("B");

    when(orders.findById(orderId)).thenReturn(Optional.of(order));
    when(details.findByLabOrderId(orderId)).thenReturn(Optional.of(detail));
    when(details.findByLabOrderIdIn(any())).thenReturn(List.of(detail));
    when(reports.findByLabOrderId(orderId)).thenReturn(Optional.of(report));
    when(reports.findByLabOrderIdIn(any())).thenReturn(List.of(report));
    when(tests.findByIdIn(any())).thenReturn(List.of(test));
    when(patients.findById(patientId)).thenReturn(Optional.of(patient));
    when(access.currentPatient()).thenReturn(Optional.of(patient));
    when(access.isLabTech()).thenReturn(false);
    when(access.isDoctor()).thenReturn(false);
    when(access.isFrontDesk()).thenReturn(false);
    when(access.isAdmin()).thenReturn(false);
    when(resultValues.findByReportIdOrderBySortOrderAsc(report.getId()))
        .thenReturn(List.of(value));
    when(storage.get(any())).thenReturn(Optional.empty());

    LabViews.ReportView view = service.report(orderId);

    assertThat(view.released()).isTrue();
    assertThat(view.values()).hasSize(1);
    assertThat(view.summary()).isEqualTo("see doctor");
    assertThat(view.abnormal()).isTrue();
  }
}
