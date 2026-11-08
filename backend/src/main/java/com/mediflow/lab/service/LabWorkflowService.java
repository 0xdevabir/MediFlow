package com.mediflow.lab.service;

import com.mediflow.lab.domain.LabCriticalTask;
import com.mediflow.lab.domain.LabOrderDetail;
import com.mediflow.lab.domain.LabReferenceRange;
import com.mediflow.lab.domain.LabResultValue;
import com.mediflow.lab.domain.ResultFlag;
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
import com.mediflow.shared.domain.DiagnosticReport;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.LabPackageItem;
import com.mediflow.shared.domain.OrderType;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Sample;
import com.mediflow.shared.domain.SampleStatus;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import com.mediflow.shared.port.ObjectStoragePort;
import com.mediflow.shared.port.PatientQrPort;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import com.mediflow.shared.repo.DiagnosticReportRepository;
import com.mediflow.shared.repo.LabOrderRepository;
import com.mediflow.shared.repo.SampleRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lab-tech queue: sample collect → receive → result upload → verify (different tech) → critical
 * phone task.
 */
@Service
public class LabWorkflowService {

  private final LabOrderRepository orders;
  private final LabOrderQueries orderQueries;
  private final LabOrderDetailRepository details;
  private final SampleRepository samples;
  private final LabSampleQueries sampleQueries;
  private final DiagnosticReportRepository reports;
  private final LabReportQueries reportQueries;
  private final LabResultValueRepository resultValues;
  private final LabReferenceRangeRepository ranges;
  private final LabCriticalTaskRepository criticalTasks;
  private final LabPackageQueries packages;
  private final LabPatientQueries patients;
  private final LabStaffQueries staff;
  private final LabAccess access;
  private final ObjectProvider<PatientQrPort> patientQr;
  private final ObjectStoragePort storage;
  private final NotificationPort notifications;
  private final ApplicationEventPublisher events;
  private final AuditPort audit;
  private final ClockPort clock;

  public LabWorkflowService(
      LabOrderRepository orders,
      LabOrderQueries orderQueries,
      LabOrderDetailRepository details,
      SampleRepository samples,
      LabSampleQueries sampleQueries,
      DiagnosticReportRepository reports,
      LabReportQueries reportQueries,
      LabResultValueRepository resultValues,
      LabReferenceRangeRepository ranges,
      LabCriticalTaskRepository criticalTasks,
      LabPackageQueries packages,
      LabPatientQueries patients,
      LabStaffQueries staff,
      LabAccess access,
      ObjectProvider<PatientQrPort> patientQr,
      ObjectStoragePort storage,
      NotificationPort notifications,
      ApplicationEventPublisher events,
      AuditPort audit,
      ClockPort clock) {
    this.orders = orders;
    this.orderQueries = orderQueries;
    this.details = details;
    this.samples = samples;
    this.sampleQueries = sampleQueries;
    this.reports = reports;
    this.reportQueries = reportQueries;
    this.resultValues = resultValues;
    this.ranges = ranges;
    this.criticalTasks = criticalTasks;
    this.packages = packages;
    this.patients = patients;
    this.staff = staff;
    this.access = access;
    this.patientQr = patientQr;
    this.storage = storage;
    this.notifications = notifications;
    this.events = events;
    this.audit = audit;
    this.clock = clock;
  }

  /** Identity check with QR token, then barcode the sample and assign the tech. */
  @PreAuthorize("hasAnyRole('LAB_TECH','ADMIN')")
  @Transactional
  public Sample collectSample(UUID labOrderId, String presentedQrToken) {
    LabOrder order = loadOrder(labOrderId);
    LabOrderDetail detail = loadDetail(labOrderId);
    access.requireCanHandle(detail);
    if (order.getStatus() != LabOrderStatus.ORDERED) {
      throw new LabRuleException("lab.error.badStatus");
    }
    if (!identityConfirmed(order, detail, presentedQrToken)) {
      throw new LabRuleException("lab.error.qrMismatch");
    }
    UUID techId = access.requireUserId();
    Instant now = clock.now();
    LocalDate day = LocalDate.now(LabSlots.CLINIC_ZONE);
    Sample sample = new Sample();
    sample.setLabOrderId(order.getId());
    sample.setBarcode(LabTokens.sampleBarcode(day));
    sample.setStatus(SampleStatus.COLLECTED);
    sample.setCollectedAt(now);
    samples.save(sample);

    detail.setAssignedTechId(techId);
    details.save(detail);

    order.setStatus(LabOrderStatus.SAMPLE_COLLECTED);
    orders.save(order);

    audit.record(
        techId,
        order.getPatientId(),
        "LAB_SAMPLE_COLLECTED",
        "lab_order",
        order.getId(),
        Map.of("barcode", sample.getBarcode()));
    return sample;
  }

  @PreAuthorize("hasAnyRole('LAB_TECH','ADMIN')")
  @Transactional
  public Sample receiveSample(UUID labOrderId) {
    LabOrder order = loadOrder(labOrderId);
    LabOrderDetail detail = loadDetail(labOrderId);
    access.requireCanHandle(detail);
    if (order.getStatus() != LabOrderStatus.SAMPLE_COLLECTED) {
      throw new LabRuleException("lab.error.badStatus");
    }
    Sample sample =
        sampleQueries
            .findByLabOrderId(order.getId())
            .orElseThrow(() -> new LabRuleException("lab.error.sampleMissing"));
    Instant now = clock.now();
    sample.setStatus(SampleStatus.RECEIVED);
    sample.setReceivedAt(now);
    samples.save(sample);
    order.setStatus(LabOrderStatus.IN_LAB);
    orders.save(order);
    audit.record(
        access.requireUserId(),
        order.getPatientId(),
        "LAB_SAMPLE_RECEIVED",
        "lab_order",
        order.getId(),
        Map.of("barcode", sample.getBarcode() == null ? "" : sample.getBarcode()));
    return sample;
  }

  /**
   * Upload a PDF (optional) plus structured analyte values. Flags are computed from reference
   * ranges. Leaves the order in {@code RESULTED}; a different tech must verify.
   */
  @PreAuthorize("hasAnyRole('LAB_TECH','ADMIN')")
  @Transactional
  public DiagnosticReport uploadResults(
      UUID labOrderId, byte[] pdfBytes, String summary, List<AnalyteInput> analytes) {
    LabOrder order = loadOrder(labOrderId);
    LabOrderDetail detail = loadDetail(labOrderId);
    access.requireCanHandle(detail);
    if (order.getStatus() != LabOrderStatus.IN_LAB && order.getStatus() != LabOrderStatus.RESULTED) {
      throw new LabRuleException("lab.error.badStatus");
    }
    if (analytes == null || analytes.isEmpty()) {
      throw new LabRuleException("lab.error.noResults");
    }
    UUID techId = access.requireUserId();
    Instant now = clock.now();
    Patient patient = patients.findById(order.getPatientId()).orElse(null);
    String sex = patient == null ? null : patient.getSex();

    DiagnosticReport report =
        reportQueries
            .findByLabOrderId(order.getId())
            .orElseGet(
                () -> {
                  DiagnosticReport r = new DiagnosticReport();
                  r.setLabOrderId(order.getId());
                  r.setAbnormalFlag(false);
                  r.setCriticalFlag(false);
                  return r;
                });
    if (report.getVerifiedAt() != null) {
      throw new LabRuleException("lab.error.alreadyVerified");
    }

    if (pdfBytes != null && pdfBytes.length > 0) {
      String key = "lab-reports/" + order.getId() + "/" + UUID.randomUUID() + ".pdf";
      storage.put(key, new ByteArrayInputStream(pdfBytes), pdfBytes.length, "application/pdf");
      report.setFileRef(key);
    }
    report.setSummary(summary);
    report.setVerifiedBy(null);
    report.setVerifiedAt(null);
    report = reports.save(report);

    resultValues.deleteByReport(report.getId());

    List<UUID> testIds = testIdsOf(order);
    List<LabReferenceRange> allRanges =
        testIds.isEmpty() ? List.of() : ranges.findByTestIdInOrderBySortOrderAsc(testIds);
    Map<String, List<LabReferenceRange>> byAnalyte = new LinkedHashMap<>();
    for (LabReferenceRange r : allRanges) {
      byAnalyte.computeIfAbsent(r.getAnalyteCode(), k -> new ArrayList<>()).add(r);
    }

    List<ResultFlag> flags = new ArrayList<>();
    int sort = 0;
    for (AnalyteInput in : analytes) {
      if (in == null || in.analyteCode() == null || in.analyteCode().isBlank()) {
        continue;
      }
      List<LabReferenceRange> candidates =
          byAnalyte.getOrDefault(in.analyteCode().trim(), List.of());
      LabReferenceRange range =
          ResultFlagCalculator.pick(candidates, sex)
              .orElse(candidates.isEmpty() ? null : candidates.get(0));
      ResultFlag flag;
      if (in.valueNumeric() != null && range != null) {
        flag = ResultFlagCalculator.flag(in.valueNumeric(), range);
      } else if (in.valueNumeric() != null) {
        flag = ResultFlag.NORMAL;
      } else if (Boolean.TRUE.equals(in.abnormalText())) {
        flag = ResultFlag.ABNORMAL;
      } else {
        flag = ResultFlag.NORMAL;
      }
      flags.add(flag);

      LabResultValue value = new LabResultValue();
      value.setReportId(report.getId());
      value.setTestId(
          range == null ? (testIds.isEmpty() ? null : testIds.get(0)) : range.getTestId());
      value.setAnalyteCode(in.analyteCode().trim());
      value.setAnalyteName(
          range != null
              ? range.getAnalyteName()
              : (in.analyteName() == null ? in.analyteCode() : in.analyteName()));
      value.setLoincCode(range == null ? in.loincCode() : range.getLoincCode());
      value.setUcumUnit(range == null ? in.ucumUnit() : range.getUcumUnit());
      value.setValueNumeric(in.valueNumeric());
      value.setValueText(in.valueText());
      if (range != null) {
        value.setRefLow(range.getRefLow());
        value.setRefHigh(range.getRefHigh());
        value.setCriticalLow(range.getCriticalLow());
        value.setCriticalHigh(range.getCriticalHigh());
      }
      value.setFlag(flag);
      value.setSortOrder(sort++);
      resultValues.save(value);
    }

    boolean abnormal = ResultFlagCalculator.anyAbnormal(flags);
    boolean critical = ResultFlagCalculator.anyCritical(flags);
    report.setAbnormalFlag(abnormal);
    report.setCriticalFlag(critical);
    reports.save(report);

    detail.setResultedBy(techId);
    detail.setResultedAt(now);
    details.save(detail);

    order.setStatus(LabOrderStatus.RESULTED);
    orders.save(order);

    sampleQueries
        .findByLabOrderId(order.getId())
        .ifPresent(
            s -> {
              s.setStatus(SampleStatus.PROCESSED);
              samples.save(s);
            });

    Map<String, Object> auditDetails = new HashMap<>();
    auditDetails.put("abnormal", abnormal);
    auditDetails.put("critical", critical);
    auditDetails.put("analyteCount", flags.size());
    audit.record(
        techId,
        order.getPatientId(),
        "LAB_RESULTS_UPLOADED",
        "diagnostic_report",
        report.getId(),
        auditDetails);
    return report;
  }

  /** Verify must be a different lab tech than the one who uploaded results. */
  @PreAuthorize("hasAnyRole('LAB_TECH','ADMIN')")
  @Transactional
  public DiagnosticReport verify(UUID labOrderId) {
    LabOrder order = loadOrder(labOrderId);
    LabOrderDetail detail = loadDetail(labOrderId);
    if (order.getStatus() != LabOrderStatus.RESULTED) {
      throw new LabRuleException("lab.error.badStatus");
    }
    UUID verifier = access.requireUserId();
    if (!access.isAdmin()
        && detail.getResultedBy() != null
        && Objects.equals(detail.getResultedBy(), verifier)) {
      throw new LabRuleException("lab.error.selfVerify");
    }
    DiagnosticReport report =
        reportQueries
            .findByLabOrderId(order.getId())
            .orElseThrow(() -> new LabRuleException("lab.error.reportMissing"));
    if (report.getVerifiedAt() != null) {
      throw new LabRuleException("lab.error.alreadyVerified");
    }
    Instant now = clock.now();
    report.setVerifiedBy(verifier);
    report.setVerifiedAt(now);
    reports.save(report);

    order.setStatus(LabOrderStatus.VERIFIED);
    orders.save(order);

    if (report.getCriticalFlag()) {
      LabCriticalTask task = new LabCriticalTask();
      task.setReportId(report.getId());
      task.setLabOrderId(order.getId());
      task.setPatientId(order.getPatientId());
      task.setAssigneeId(verifier);
      task.setStatus(LabCriticalTask.Status.OPEN);
      criticalTasks.save(task);
      alertDutyDoctors(order, report);
    }

    events.publishEvent(
        new DomainEvents.LabReportVerified(
            report.getId(),
            order.getId(),
            order.getPatientId(),
            report.getAbnormalFlag(),
            report.getCriticalFlag(),
            now));

    if (order.getOrderType() == OrderType.DOCTOR && order.getOrderedBy() != null) {
      notifications.notifyUser(
          order.getOrderedBy(),
          "IN_APP",
          "en",
          "Lab report verified",
          "A lab report for your patient is verified"
              + (report.getCriticalFlag()
                  ? " (CRITICAL)."
                  : report.getAbnormalFlag() ? " (abnormal)." : "."));
    }

    audit.record(
        verifier,
        order.getPatientId(),
        "LAB_REPORT_VERIFIED",
        "diagnostic_report",
        report.getId(),
        Map.of(
            "abnormal", report.getAbnormalFlag(),
            "critical", report.getCriticalFlag(),
            "orderType", order.getOrderType().name()));
    return report;
  }

  @PreAuthorize("hasAnyRole('LAB_TECH','ADMIN')")
  @Transactional
  public LabCriticalTask completeCriticalTask(UUID taskId, String outcomeNote) {
    LabCriticalTask task =
        criticalTasks
            .findById(taskId)
            .orElseThrow(() -> new LabRuleException("lab.error.taskNotFound"));
    if (task.getStatus() == LabCriticalTask.Status.DONE) {
      throw new LabRuleException("lab.error.taskDone");
    }
    UUID actor = access.requireUserId();
    if (!access.isAdmin() && !Objects.equals(task.getAssigneeId(), actor)) {
      throw new LabRuleException("lab.error.taskNotYours");
    }
    if (outcomeNote == null || outcomeNote.trim().length() < 5) {
      throw new LabRuleException("lab.error.taskNoteRequired");
    }
    Instant now = clock.now();
    task.setStatus(LabCriticalTask.Status.DONE);
    task.setDoneBy(actor);
    task.setDoneAt(now);
    task.setOutcomeNote(outcomeNote.trim());
    criticalTasks.save(task);
    audit.record(
        actor,
        task.getPatientId(),
        "LAB_CRITICAL_PHONE_DONE",
        "lab_critical_task",
        task.getId(),
        Map.of("reportId", task.getReportId().toString()));
    return task;
  }

  private void alertDutyDoctors(LabOrder order, DiagnosticReport report) {
    for (UUID doctorUserId : staff.dutyDoctorUserIds(order.getHospitalId())) {
      notifications.notifyUser(
          doctorUserId,
          "IN_APP",
          "en",
          "Critical lab value",
          "Critical lab result verified for a patient at your hospital. Order "
              + order.getId()
              + " report "
              + report.getId());
    }
  }

  private List<UUID> testIdsOf(LabOrder order) {
    List<UUID> ids = new ArrayList<>();
    if (order.getTestId() != null) {
      ids.add(order.getTestId());
    }
    if (order.getPackageId() != null) {
      for (LabPackageItem item : packages.items(order.getPackageId())) {
        ids.add(item.getTestId());
      }
    }
    return ids;
  }


  /**
   * Accept either the order booking QR token or a patient-identity token from {@link
   * PatientQrPort} (V2-03) when that bean is present.
   */
  private boolean identityConfirmed(
      LabOrder order, LabOrderDetail detail, String presentedQrToken) {
    if (LabTokens.tokenMatches(detail.getQrToken(), presentedQrToken)) {
      return true;
    }
    PatientQrPort qr = patientQr.getIfAvailable();
    if (qr == null || presentedQrToken == null || presentedQrToken.isBlank()) {
      return false;
    }
    Optional<UUID> resolved = qr.resolvePatientId(presentedQrToken.trim());
    return resolved.isPresent() && resolved.get().equals(order.getPatientId());
  }

  private LabOrder loadOrder(UUID id) {
    return orderQueries
        .findById(id)
        .or(() -> orders.findById(id))
        .orElseThrow(() -> new LabRuleException("lab.error.orderNotFound"));
  }

  private LabOrderDetail loadDetail(UUID labOrderId) {
    return details
        .findByLabOrderId(labOrderId)
        .orElseThrow(() -> new LabRuleException("lab.error.orderNotFound"));
  }

  /** One structured analyte line entered by the lab tech. */
  public record AnalyteInput(
      String analyteCode,
      String analyteName,
      String loincCode,
      String ucumUnit,
      BigDecimal valueNumeric,
      String valueText,
      Boolean abnormalText) {}
}
