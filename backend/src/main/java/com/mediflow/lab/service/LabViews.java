package com.mediflow.lab.service;

import com.mediflow.lab.domain.LabCriticalTask;
import com.mediflow.lab.domain.LabReferenceRange;
import com.mediflow.lab.domain.LabResultValue;
import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.OrderType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read models handed to the lab templates. */
public final class LabViews {

  private LabViews() {}

  /** One orderable item (test or package) with its price. */
  public record CatalogItem(
      UUID id, String code, String name, String loincCode, BigDecimal price, boolean pkg, List<String> includes) {}

  /** Price quote of a direct booking, before it is saved. */
  public record Quote(
      UUID hospitalId,
      String hospitalName,
      List<CatalogItem> items,
      BigDecimal total,
      LocalDate date,
      String slot) {}

  /** Result of a saved direct booking. */
  public record DirectBooking(
      String bookingRef, List<UUID> orderIds, BigDecimal total, LocalDate date, String slot) {}

  /** One lab order as shown in lists. {@code released} = report verified and visible. */
  public record OrderRow(
      UUID orderId,
      String bookingRef,
      String itemName,
      OrderType orderType,
      LabOrderStatus status,
      Instant orderedAt,
      LocalDate scheduledDate,
      String slot,
      BigDecimal price,
      UUID patientId,
      String patientName,
      String patientMrn,
      UUID assignedTechId,
      UUID resultedBy,
      boolean released,
      boolean abnormal,
      boolean critical) {}

  /**
   * A report as a viewer may see it. For a patient before verification {@code released} is false and
   * {@code values} is empty, {@code summary} null, {@code hasPdf} false.
   */
  public record ReportView(
      OrderRow order,
      boolean released,
      boolean abnormal,
      boolean critical,
      String summary,
      boolean hasPdf,
      Instant verifiedAt,
      List<LabResultValue> values) {}

  /** What a lab tech sees on one order: the order, analytes to enter and the current report. */
  public record TechOrderView(
      OrderRow order,
      String sampleBarcode,
      List<LabReferenceRange> analytes,
      ReportView report,
      String rejectedReason,
      boolean canCollect,
      boolean canReceive,
      boolean canUpload,
      boolean canVerify) {}

  public record CriticalTaskRow(LabCriticalTask task, OrderRow order) {}
}
