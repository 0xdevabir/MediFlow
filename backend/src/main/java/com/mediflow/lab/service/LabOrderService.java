package com.mediflow.lab.service;

import com.mediflow.lab.domain.BookingChannel;
import com.mediflow.lab.domain.LabOrderDetail;
import com.mediflow.lab.repo.LabCatalogQueries;
import com.mediflow.lab.repo.LabEncounterQueries;
import com.mediflow.lab.repo.LabHospitalQueries;
import com.mediflow.lab.repo.LabOrderDetailRepository;
import com.mediflow.lab.repo.LabPackageQueries;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.Hospital;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.LabPackage;
import com.mediflow.shared.domain.LabPackageItem;
import com.mediflow.shared.domain.LabTest;
import com.mediflow.shared.domain.OrderType;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.LabOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates doctor-ordered and direct lab orders. Direct booking is restricted to {@code
 * direct_allowed} catalogue entries (server-side).
 */
@Service
public class LabOrderService {

  private final LabOrderRepository orders;
  private final LabOrderDetailRepository details;
  private final LabCatalogQueries tests;
  private final LabPackageQueries packages;
  private final LabEncounterQueries encounters;
  private final LabHospitalQueries hospitals;
  private final LabAccess access;
  private final ApplicationEventPublisher events;
  private final AuditPort audit;
  private final ClockPort clock;

  public LabOrderService(
      LabOrderRepository orders,
      LabOrderDetailRepository details,
      LabCatalogQueries tests,
      LabPackageQueries packages,
      LabEncounterQueries encounters,
      LabHospitalQueries hospitals,
      LabAccess access,
      ApplicationEventPublisher events,
      AuditPort audit,
      ClockPort clock) {
    this.orders = orders;
    this.details = details;
    this.tests = tests;
    this.packages = packages;
    this.encounters = encounters;
    this.hospitals = hospitals;
    this.access = access;
    this.events = events;
    this.audit = audit;
    this.clock = clock;
  }

  /**
   * Shared doctor-order path used by {@code /lab/encounters/{id}/orders} and {@link
   * com.mediflow.shared.port.LabOrderPort}. Resolves {@code testCode} (code / LOINC / name), creates
   * a real {@code lab_order}, publishes {@code LabOrderCreated} (+ {@code LabOrderRequested} for
   * listeners still expecting the consult event), and returns the order id (never nil UUID).
   */
  @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
  @Transactional
  public UUID requestOrderByTestCode(
      UUID encounterId, UUID patientId, UUID hospitalId, String testCode) {
    if (testCode == null || testCode.isBlank()) {
      throw new LabRuleException("lab.error.noTests");
    }
    Encounter encounter = requireEncounterForOrder(encounterId, patientId, hospitalId);
    LabTest test = resolveTest(testCode.trim());
    UUID actor = access.requireUserId();
    Instant now = clock.now();
    LabOrder order = createDoctorOrder(encounter, test, actor, now, LabTokens.bookingRef());
    events.publishEvent(
        new DomainEvents.LabOrderRequested(
            encounter.getId(),
            encounter.getPatientId(),
            encounter.getHospitalId(),
            testCode.trim(),
            actor,
            now));
    return order.getId();
  }

  /** Doctor orders one or more tests against an open encounter (same persistence as the port). */
  @PreAuthorize("hasRole('DOCTOR')")
  @Transactional
  public List<LabOrder> orderForEncounter(UUID encounterId, List<UUID> testIds) {
    if (testIds == null || testIds.isEmpty()) {
      throw new LabRuleException("lab.error.noTests");
    }
    Encounter encounter =
        encounters
            .findById(encounterId)
            .orElseThrow(() -> new LabRuleException("lab.error.encounterNotFound"));
    UUID actor = access.requireUserId();
    Instant now = clock.now();
    String bookingRef = LabTokens.bookingRef();
    List<LabOrder> created = new ArrayList<>();
    Set<UUID> unique = new LinkedHashSet<>(testIds);
    for (UUID testId : unique) {
      LabTest test =
          tests.findById(testId).orElseThrow(() -> new LabRuleException("lab.error.testNotFound"));
      if (!test.getActive()) {
        throw new LabRuleException("lab.error.testInactive");
      }
      created.add(createDoctorOrder(encounter, test, actor, now, bookingRef));
    }
    return created;
  }

  /**
   * Resolve catalogue entry by internal code, LOINC, exact name, or a unique name substring (active
   * only).
   */
  public LabTest resolveTest(String testCode) {
    String q = testCode.trim();
    Optional<LabTest> byCode = tests.findByCodeIgnoreCase(q);
    if (byCode.isPresent()) {
      return requireActive(byCode.get());
    }
    Optional<LabTest> byLoinc = tests.findByLoincCodeIgnoreCase(q);
    if (byLoinc.isPresent()) {
      return requireActive(byLoinc.get());
    }
    Optional<LabTest> byName = tests.findFirstByActiveTrueAndNameIgnoreCase(q);
    if (byName.isPresent()) {
      return byName.get();
    }
    List<LabTest> partial = tests.findByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(q);
    if (partial.size() == 1) {
      return partial.get(0);
    }
    throw new LabRuleException("lab.error.testNotFound");
  }

  private static LabTest requireActive(LabTest test) {
    if (!test.getActive()) {
      throw new LabRuleException("lab.error.testInactive");
    }
    return test;
  }

  private Encounter requireEncounterForOrder(UUID encounterId, UUID patientId, UUID hospitalId) {
    Encounter encounter =
        encounters
            .findById(encounterId)
            .orElseThrow(() -> new LabRuleException("lab.error.encounterNotFound"));
    if (patientId != null && !patientId.equals(encounter.getPatientId())) {
      throw new LabRuleException("lab.error.encounterMismatch");
    }
    if (hospitalId != null && !hospitalId.equals(encounter.getHospitalId())) {
      throw new LabRuleException("lab.error.encounterMismatch");
    }
    return encounter;
  }

  private LabOrder createDoctorOrder(
      Encounter encounter, LabTest test, UUID actor, Instant now, String bookingRef) {
    LabOrder order =
        saveOrder(
            encounter.getPatientId(),
            encounter.getHospitalId(),
            encounter.getId(),
            test.getId(),
            null,
            OrderType.DOCTOR,
            actor,
            now);
    saveDetail(
        order,
        bookingRef,
        actor,
        BookingChannel.DOCTOR,
        null,
        null,
        test.getPrice() == null ? BigDecimal.ZERO : test.getPrice());
    publishCreated(order, now);
    audit.record(
        actor,
        order.getPatientId(),
        "LAB_ORDER_CREATED",
        "lab_order",
        order.getId(),
        Map.of(
            "orderType", OrderType.DOCTOR.name(),
            "testId", test.getId().toString(),
            "encounterId", encounter.getId().toString()));
    return order;
  }

  /** Signed-in patient books direct-allowed tests/packages for themself. */
  @PreAuthorize("hasRole('PATIENT')")
  @Transactional
  public LabViews.DirectBooking bookDirectForSelf(
      UUID hospitalId, List<UUID> testIds, List<UUID> packageIds, LocalDate date, String slot) {
    UUID patientId = access.requireCurrentPatientId();
    return bookDirect(patientId, hospitalId, testIds, packageIds, date, slot, BookingChannel.PATIENT);
  }

  /** Front desk books a walk-in direct test by MRN. */
  @PreAuthorize("hasRole('FRONT_DESK')")
  @Transactional
  public LabViews.DirectBooking bookDirectForWalkIn(
      String mrn,
      UUID hospitalId,
      List<UUID> testIds,
      List<UUID> packageIds,
      LocalDate date,
      String slot) {
    Patient patient = access.requirePatientByMrn(mrn);
    return bookDirect(
        patient.getId(), hospitalId, testIds, packageIds, date, slot, BookingChannel.FRONT_DESK);
  }

  /** Price quote before saving (same validation as booking). */
  @PreAuthorize("hasAnyRole('PATIENT','FRONT_DESK')")
  @Transactional(readOnly = true)
  public LabViews.Quote quote(
      UUID hospitalId, List<UUID> testIds, List<UUID> packageIds, LocalDate date, String slot) {
    Hospital hospital = requireHospital(hospitalId);
    validateSlot(date, slot);
    ResolvedItems resolved = resolveDirectItems(hospitalId, testIds, packageIds);
    return new LabViews.Quote(
        hospital.getId(), hospital.getName(), resolved.items(), resolved.total(), date, slot);
  }

  private LabViews.DirectBooking bookDirect(
      UUID patientId,
      UUID hospitalId,
      List<UUID> testIds,
      List<UUID> packageIds,
      LocalDate date,
      String slot,
      BookingChannel channel) {
    Hospital hospital = requireHospital(hospitalId);
    validateSlot(date, slot);
    ResolvedItems resolved = resolveDirectItems(hospitalId, testIds, packageIds);
    UUID actor = access.requireUserId();
    Instant now = clock.now();
    String bookingRef = LabTokens.bookingRef();
    List<UUID> orderIds = new ArrayList<>();
    for (ResolvedLine line : resolved.lines()) {
      LabOrder order =
          saveOrder(
              patientId,
              hospital.getId(),
              null,
              line.testId(),
              line.packageId(),
              OrderType.DIRECT,
              actor,
              now);
      saveDetail(order, bookingRef, actor, channel, date, slot, line.price());
      publishCreated(order, now);
      orderIds.add(order.getId());
      Map<String, Object> details = new HashMap<>();
      details.put("orderType", OrderType.DIRECT.name());
      details.put("channel", channel.name());
      details.put("bookingRef", bookingRef);
      if (line.testId() != null) {
        details.put("testId", line.testId().toString());
      }
      if (line.packageId() != null) {
        details.put("packageId", line.packageId().toString());
      }
      audit.record(actor, patientId, "LAB_ORDER_CREATED", "lab_order", order.getId(), details);
    }
    return new LabViews.DirectBooking(bookingRef, orderIds, resolved.total(), date, slot);
  }

  private ResolvedItems resolveDirectItems(
      UUID hospitalId, List<UUID> testIds, List<UUID> packageIds) {
    List<UUID> testsIn = testIds == null ? List.of() : testIds;
    List<UUID> pkgsIn = packageIds == null ? List.of() : packageIds;
    if (testsIn.isEmpty() && pkgsIn.isEmpty()) {
      throw new LabRuleException("lab.error.noTests");
    }
    List<LabViews.CatalogItem> catalogItems = new ArrayList<>();
    List<ResolvedLine> lines = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;

    for (UUID testId : new LinkedHashSet<>(testsIn)) {
      LabTest test =
          tests.findById(testId).orElseThrow(() -> new LabRuleException("lab.error.testNotFound"));
      if (!test.getActive() || !test.getDirectAllowed()) {
        throw new LabRuleException("lab.error.notDirectAllowed");
      }
      BigDecimal price = test.getPrice() == null ? BigDecimal.ZERO : test.getPrice();
      catalogItems.add(
          new LabViews.CatalogItem(
              test.getId(),
              test.getCode(),
              test.getName(),
              test.getLoincCode(),
              price,
              false,
              List.of()));
      lines.add(new ResolvedLine(test.getId(), null, price));
      total = total.add(price);
    }

    for (UUID packageId : new LinkedHashSet<>(pkgsIn)) {
      LabPackage pkg =
          packages
              .findById(packageId)
              .orElseThrow(() -> new LabRuleException("lab.error.packageNotFound"));
      if (!pkg.getActive()) {
        throw new LabRuleException("lab.error.packageInactive");
      }
      if (pkg.getHospitalId() != null && !pkg.getHospitalId().equals(hospitalId)) {
        throw new LabRuleException("lab.error.packageWrongHospital");
      }
      List<LabPackageItem> items = packages.items(packageId);
      Map<UUID, LabTest> byId = new HashMap<>();
      for (LabPackageItem item : items) {
        LabTest t =
            tests
                .findById(item.getTestId())
                .orElseThrow(() -> new LabRuleException("lab.error.testNotFound"));
        byId.put(t.getId(), t);
      }
      if (!LabCatalogService.allDirectAllowed(items, byId)) {
        throw new LabRuleException("lab.error.notDirectAllowed");
      }
      BigDecimal price = pkg.getPrice() == null ? BigDecimal.ZERO : pkg.getPrice();
      List<String> names = byId.values().stream().map(LabTest::getName).toList();
      catalogItems.add(
          new LabViews.CatalogItem(
              pkg.getId(),
              "PKG-" + pkg.getId().toString().substring(0, 8),
              pkg.getName(),
              null,
              price,
              true,
              names));
      lines.add(new ResolvedLine(null, pkg.getId(), price));
      total = total.add(price);
    }
    return new ResolvedItems(catalogItems, lines, total);
  }

  private Hospital requireHospital(UUID hospitalId) {
    return hospitals
        .findById(hospitalId)
        .filter(Hospital::getActive)
        .orElseThrow(() -> new LabRuleException("lab.error.hospitalNotFound"));
  }

  private void validateSlot(LocalDate date, String slot) {
    if (date == null || slot == null || slot.isBlank()) {
      throw new LabRuleException("lab.error.slotRequired");
    }
    if (!LabSlots.WINDOWS.contains(slot.trim())) {
      throw new LabRuleException("lab.error.slotInvalid");
    }
    LocalDate today = LocalDate.now(LabSlots.CLINIC_ZONE);
    if (date.isBefore(today) || date.isAfter(today.plusDays(LabSlots.MAX_DAYS_AHEAD))) {
      throw new LabRuleException("lab.error.dateOutOfRange");
    }
  }

  private LabOrder saveOrder(
      UUID patientId,
      UUID hospitalId,
      UUID encounterId,
      UUID testId,
      UUID packageId,
      OrderType type,
      UUID orderedBy,
      Instant now) {
    LabOrder order = new LabOrder();
    order.setPatientId(patientId);
    order.setHospitalId(hospitalId);
    order.setEncounterId(encounterId);
    order.setTestId(testId);
    order.setPackageId(packageId);
    order.setOrderType(type);
    order.setStatus(LabOrderStatus.ORDERED);
    order.setOrderedBy(orderedBy);
    order.setOrderedAt(now);
    return orders.save(order);
  }

  private void saveDetail(
      LabOrder order,
      String bookingRef,
      UUID bookedBy,
      BookingChannel channel,
      LocalDate date,
      String slot,
      BigDecimal price) {
    LabOrderDetail detail = new LabOrderDetail();
    detail.setLabOrderId(order.getId());
    detail.setBookingRef(bookingRef);
    detail.setBookedBy(bookedBy);
    detail.setBookedVia(channel);
    detail.setScheduledDate(date);
    detail.setSlotLabel(slot);
    detail.setPrice(price);
    detail.setQrToken(LabTokens.qrToken());
    details.save(detail);
  }

  private void publishCreated(LabOrder order, Instant now) {
    events.publishEvent(
        new DomainEvents.LabOrderCreated(
            order.getId(),
            order.getPatientId(),
            order.getHospitalId(),
            order.getOrderType().name(),
            now));
  }

  private record ResolvedLine(UUID testId, UUID packageId, BigDecimal price) {}

  private record ResolvedItems(
      List<LabViews.CatalogItem> items, List<ResolvedLine> lines, BigDecimal total) {}
}
