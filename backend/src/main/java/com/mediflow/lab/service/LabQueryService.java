LabQueryService.java 236L cognitive
// /Users/mdabirhossain/Documents/WebDevelopment/Shared-Startups/MediFlow-wt/T07-lab/backend/src/main/java/com/mediflow/lab/service/LabQueryService.java
§ function function (L1-L48)
LabQueryService.java 366L cognitive
// /Users/mdabirhossain/Documents/WebDevelopment/Shared-Startups/MediFlow-wt/T07-lab/backend/src/main/java/com/mediflow/lab/service/LabQueryService.java
§ function function (L110-L154)
  @PreAuthorize("hasAnyRole('PATIENT','DOCTOR','LAB_TECH','FRONT_DESK','ADMIN')")
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
    boolean patientViewer = access.currentPatient().map(p -> p.getId().equals(order.getPatientId())).orElse(false)
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
// ... 4 lines omitted
§ function function (L53-L75)
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
    // Lab tech only sees unassigned orders or ones assigned to them.
    if (access.isAdmin()) {
      return rows;
    }
    return rows.stream()
        .filter(r -> r.assignedTechId() == null || me.equals(r.assignedTechId()))
        .toList();
  }
// ... 4 lines omitted
§ function function (L80-L115)
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
// ... 4 lines omitted
§ function function (L120-L142)
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
// ... 2 lines omitted
§ function function (L145-L205)
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
        patientById.computeIfAbsent(
            o.getPatientId(), id -> patients.findById(id).orElse(null));
      }
    }
    List<LabViews.OrderRow> out = new ArrayList<>(list.size());
    for (LabOrder o : list) {
      LabOrderDetail d = detailByOrder.get(o.getId());
      DiagnosticReport r = reportByOrder.get(o.getId());
      String name = itemName(o, testById, pkgById);
      Patient p = patientById.get(o.getPatientId());
      String patientName =
          p == null ? null : (p.getGivenName() + " " + p.getFamilyName()).trim();
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
// ... 2 lines omitted
§ function function (L208-L219)
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
// ... 2 lines omitted
§ function function (L222-L234)
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
7/17 chunks shown (2326 tokens)
[lean-ctx] full source: read "/Users/mdabirhossain/Documents/WebDevelopment/Shared-Startups/MediFlow-wt/T07-lab/backend/src/main/java/com/mediflow/lab/service/LabQueryService.java" directly (no MCP)  ·  or ctx_read("/Users/mdabirhossain/Documents/WebDevelopment/Shared-Startups/MediFlow-wt/T07-lab/backend/src/main/java/com/mediflow/lab/service/LabQueryService.java", mode="full")
