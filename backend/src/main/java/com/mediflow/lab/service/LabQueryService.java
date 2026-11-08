package com.mediflow.lab.service;

import com.mediflow.lab.domain.LabCriticalTask;
import com.mediflow.lab.domain.LabOrderDetail;
import com.mediflow.lab.domain.LabReferenceRange;
import com.mediflow.lab.domain.LabResultValue;
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
import com.mediflow.shared.domain.DiagnosticReport;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.LabPackage;
import com.mediflow.shared.domain.LabPackageItem;
import com.mediflow.shared.domain.LabTest;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Sample;
import com.mediflow.shared.port.ObjectStoragePort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles read models for patient reports and the lab-tech queue. */
@Service
public class LabQueryService {

  private static final Set<LabOrderStatus> TECH_QUEUE =
      EnumSet.of(
          LabOrderStatus.ORDERED,
          LabOrderStatus.SAMPLE_COLLECTED,
          LabOrderStatus.IN_LAB,
          LabOrderStatus.RESULTED);

  private final LabOrderQueries orders;
  private final LabOrderDetailRepository details;
  private final LabReportQueries reports;
  private final LabResultValueRepository resultValues;
  private final LabReferenceRangeRepository ranges;
  private final LabSampleQueries samples;
  private final LabCatalogQueries tests;
  private final LabPackageQueries packages;
  private final LabPatientQueries patients;
  private final LabCriticalTaskRepository criticalTasks;
  private final LabStaffQueries staff;
  private final LabAccess access;
  private final ObjectStoragePort storage;

  public LabQueryService(
      LabOrderQueries orders,
      LabOrderDetailRepository details,
      LabReportQueries reports,
      LabResultValueRepository resultValues,
      LabReferenceRangeRepository ranges,
      LabSampleQueries samples,
      LabCatalogQueries tests,
      LabPackageQueries packages,
      LabPatientQueries patients,
      LabCriticalTaskRepository criticalTasks,
      LabStaffQueries staff,
      LabAccess access,
      ObjectStoragePort storage) {
    this.orders = orders;
    this.details = details;
    this.reports = reports;
    this.resultValues = resultValues;
    this.ranges = ranges;
    this.samples = samples;
    this.tests = tests;
    this.packages = packages;
    this.patients = patients;
    this.criticalTasks = criticalTasks;
    this.staff = staff;
    this.access = access;
    this.storage = storage;
  }

  @PreAuthorize("hasRole('PATIENT')")
  @Transactional(readOnly = true)
  public List<LabViews.OrderRow> myOrders() {
    UUID patientId = access.requireCurrentPatientId();
    return toRows(orders.findByPatientIdOrderByOrderedAtDesc(patientId), false);
  }

  /**
   * Patient report view: values and PDF only after verification (safety invariant). Staff/doctor may
   * see draft values when they can view the order.
   */
  @PreAuthorize(
      "@access.canReadLabOrder(#labOrderId) or hasAnyRole('FRONT_DESK','ADMIN')")
  @Transactional(readOnly = true)
  public LabViews.ReportView report(UUID labOrderId) {
    LabOrder order =
        orders.findById(labOrderId).orElseThrow(() -> new LabRuleException("lab.error.orderNotFound"));
    LabOrderDetail detail =
        details
            .findByLabOrderId(labOrderId)
            .orElseThrow(() -> new LabRuleException("lab.error.orderNotFound"));
    access.requireCanViewOrder(order, detail);
    LabViews.OrderRow row = toRows(List.of(order), true).get(0);
    Optional<DiagnosticReport> report = reports.findByLabOrderId(labOrderId);
    boolean released = order.getStatus() == LabOrderStatus.VERIFIED && report.isPresent();
    boolean patientViewer =
        access.currentPatient().map(p -> p.getId().equals(order.getPatientId())).orElse(false)
            && !access.isLabTech()
            && !access.isDoctor()
            && !access.isFrontDesk()
            && !access.isAdmin();

    if (patientViewer && !released) {
      return new LabViews.ReportView(row, false, false, false, null, false, null, List.of());
    }
    if (report.isEmpty()) {
      return new LabViews.ReportView(row, false, false, false, null, false, null, List.of());
    }
    DiagnosticReport r = report.get();
    List<LabResultValue> values =
        released || !patientViewer
            ? resultValues.findByReportIdOrderBySortOrderAsc(r.getId())
            : List.of();
    boolean hasPdf =
        (released || !patientViewer)
            && r.getFileRef() != null
            && !r.getFileRef().isBlank()
            && storage.get(r.getFileRef()).isPresent();
    return new LabViews.ReportView(
        row,
        released,
        r.getAbnormalFlag(),
        r.getCriticalFlag(),
        (released || !patientViewer) ? r.getSummary() : null,
        hasPdf,
        r.getVerifiedAt(),
        values);
  }

  @PreAuthorize("hasAnyRole('LAB_TECH','ADMIN')")
  @Transactional(readOnly = true)
  public List<LabViews.OrderRow> techQueue(LabOrderStatus statusFilter) {
    UUID me = access.requireUserId();
    List<UUID> hospitals = staff.hospitalIdsOf(me);
    List<LabOrder> list;
    Collection<LabOrderStatus> statuses =
        statusFilter == null ? TECH_QUEUE : EnumSet.of(statusFilter);
    if (hospitals.isEmpty() || access.isAdmin()) {
      list = orders.findByStatusInOrderByOrderedAtAsc(statuses);
    } else {
      list = new ArrayList<>();
      for (UUID hid : hospitals) {
        list.addAll(orders.findByHospitalIdAndStatusInOrderByOrderedAtAsc(hid, statuses));
      }
    }
    List<LabViews.OrderRow> rows = toRows(list, true);
    if (access.isAdmin()) {
      return rows;
    }
    return rows.stream()
        .filter(r -> r.assignedTechId() == null || me.equals(r.assignedTechId()))
        .toList();
  }

  @PreAuthorize("@access.canReadLabOrder(#labOrderId) or hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public LabViews.TechOrderView techOrder(UUID labOrderId) {
    LabOrder order =
        orders.findById(labOrderId).orElseThrow(() -> new LabRuleException("lab.error.orderNotFound"));
    LabOrderDetail detail =
        details
            .findByLabOrderId(labOrderId)
            .orElseThrow(() -> new LabRuleException("lab.error.orderNotFound"));
    access.requireCanHandle(detail);
    LabViews.OrderRow row = toRows(List.of(order), true).get(0);
    Sample sample = samples.findByLabOrderId(labOrderId).orElse(null);
    List<UUID> testIds = testIdsOf(order);
    List<LabReferenceRange> analytes =
        testIds.isEmpty()
            ? List.of()
            : dedupeAnalytes(ranges.findByTestIdInOrderBySortOrderAsc(testIds), order);
    LabViews.ReportView report = report(labOrderId);
    boolean canCollect = order.getStatus() == LabOrderStatus.ORDERED;
    boolean canReceive = order.getStatus() == LabOrderStatus.SAMPLE_COLLECTED;
    boolean canUpload =
        order.getStatus() == LabOrderStatus.IN_LAB || order.getStatus() == LabOrderStatus.RESULTED;
    boolean canVerify =
        order.getStatus() == LabOrderStatus.RESULTED
            && (access.isAdmin()
                || detail.getResultedBy() == null
                || !detail.getResultedBy().equals(access.requireUserId()));
    return new LabViews.TechOrderView(
        row,
        sample == null ? null : sample.getBarcode(),
        analytes,
        report,
        detail.getRejectedReason(),
        canCollect,
        canReceive,
        canUpload,
        canVerify);
  }

  /** QR token for printing — lab tech / front desk only. */
  @PreAuthorize(
      "@access.canReadLabOrder(#labOrderId) or hasAnyRole('FRONT_DESK','ADMIN')")
  @Transactional(readOnly = true)
  public String qrToken(UUID labOrderId) {
    LabOrder order =
        orders.findById(labOrderId).orElseThrow(() -> new LabRuleException("lab.error.orderNotFound"));
    LabOrderDetail detail =
        details
            .findByLabOrderId(labOrderId)
            .orElseThrow(() -> new LabRuleException("lab.error.orderNotFound"));
    access.requireCanViewOrder(order, detail);
    return detail.getQrToken();
  }

  @PreAuthorize("hasAnyRole('LAB_TECH','ADMIN')")
  @Transactional(readOnly = true)
  public List<LabViews.CriticalTaskRow> openCriticalTasks() {
    UUID me = access.requireUserId();
    List<LabCriticalTask> tasks =
        access.isAdmin()
            ? criticalTasks.findByStatusOrderByCreatedAtAsc(LabCriticalTask.Status.OPEN)
            : criticalTasks.findByAssigneeIdAndStatusOrderByCreatedAtAsc(
                me, LabCriticalTask.Status.OPEN);
    if (tasks.isEmpty()) {
      return List.of();
    }
    List<UUID> orderIds = tasks.stream().map(LabCriticalTask::getLabOrderId).toList();
    Map<UUID, LabViews.OrderRow> byId =
        toRows(orders.findByIdIn(orderIds), true).stream()
            .collect(Collectors.toMap(LabViews.OrderRow::orderId, Function.identity(), (a, b) -> a));
    List<LabViews.CriticalTaskRow> out = new ArrayList<>();
    for (LabCriticalTask t : tasks) {
      LabViews.OrderRow row = byId.get(t.getLabOrderId());
      if (row != null) {
        out.add(new LabViews.CriticalTaskRow(t, row));
      }
    }
    return out;
  }

  @PreAuthorize("hasRole('DOCTOR')")
  @Transactional(readOnly = true)
  public List<LabViews.OrderRow> ordersForEncounter(UUID encounterId) {
    return toRows(orders.findByEncounterIdOrderByOrderedAtAsc(encounterId), true);
  }

  private List<LabViews.OrderRow> toRows(List<LabOrder> list, boolean includePatient) {
    if (list.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = list.stream().map(LabOrder::getId).toList();
    Map<UUID, LabOrderDetail> detailByOrder =
        details.findByLabOrderIdIn(ids).stream()
            .collect(Collectors.toMap(LabOrderDetail::getLabOrderId, Function.identity(), (a, b) -> a));
    Map<UUID, DiagnosticReport> reportByOrder =
        reports.findByLabOrderIdIn(ids).stream()
            .collect(Collectors.toMap(DiagnosticReport::getLabOrderId, Function.identity(), (a, b) -> a));
    List<UUID> testIds =
        list.stream().map(LabOrder::getTestId).filter(Objects::nonNull).distinct().toList();
    Map<UUID, LabTest> testById =
        testIds.isEmpty()
            ? Map.of()
            : tests.findByIdIn(testIds).stream()
                .collect(Collectors.toMap(LabTest::getId, Function.identity(), (a, b) -> a));
    Map<UUID, LabPackage> pkgById = new HashMap<>();
    for (LabOrder o : list) {
      if (o.getPackageId() != null && !pkgById.containsKey(o.getPackageId())) {
        packages.findById(o.getPackageId()).ifPresent(p -> pkgById.put(p.getId(), p));
      }
    }
    Map<UUID, Patient> patientById = new HashMap<>();
    if (includePatient) {
      for (LabOrder o : list) {
        patientById.computeIfAbsent(o.getPatientId(), id -> patients.findById(id).orElse(null));
      }
    }
    List<LabViews.OrderRow> out = new ArrayList<>(list.size());
    for (LabOrder o : list) {
      LabOrderDetail d = detailByOrder.get(o.getId());
      DiagnosticReport r = reportByOrder.get(o.getId());
      String name = itemName(o, testById, pkgById);
      Patient p = patientById.get(o.getPatientId());
      String patientName = p == null ? null : (p.getGivenName() + " " + p.getFamilyName()).trim();
      String mrn = p == null ? null : p.getMrn();
      boolean released = o.getStatus() == LabOrderStatus.VERIFIED && r != null;
      out.add(
          new LabViews.OrderRow(
              o.getId(),
              d == null ? null : d.getBookingRef(),
              name,
              o.getOrderType(),
              o.getStatus(),
              o.getOrderedAt(),
              d == null ? null : d.getScheduledDate(),
              d == null ? null : d.getSlotLabel(),
              d == null ? null : d.getPrice(),
              o.getPatientId(),
              patientName,
              mrn,
              d == null ? null : d.getAssignedTechId(),
              d == null ? null : d.getResultedBy(),
              released,
              r != null && r.getAbnormalFlag(),
              r != null && r.getCriticalFlag()));
    }
    return out;
  }

  private static String itemName(
      LabOrder o, Map<UUID, LabTest> tests, Map<UUID, LabPackage> pkgs) {
    if (o.getTestId() != null) {
      LabTest t = tests.get(o.getTestId());
      return t == null ? o.getTestId().toString() : t.getName();
    }
    if (o.getPackageId() != null) {
      LabPackage p = pkgs.get(o.getPackageId());
      return p == null ? o.getPackageId().toString() : p.getName();
    }
    return "Lab order";
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

  private List<LabReferenceRange> dedupeAnalytes(List<LabReferenceRange> all, LabOrder order) {
    Patient patient = patients.findById(order.getPatientId()).orElse(null);
    String sex = patient == null ? null : patient.getSex();
    Map<String, LabReferenceRange> picked = new LinkedHashMap<>();
    Map<String, List<LabReferenceRange>> byCode = new LinkedHashMap<>();
    for (LabReferenceRange r : all) {
      byCode.computeIfAbsent(r.getAnalyteCode(), k -> new ArrayList<>()).add(r);
    }
    for (Map.Entry<String, List<LabReferenceRange>> e : byCode.entrySet()) {
      ResultFlagCalculator.pick(e.getValue(), sex).ifPresent(r -> picked.put(e.getKey(), r));
    }
    return new ArrayList<>(picked.values());
  }
}
