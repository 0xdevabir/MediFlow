package com.mediflow.lab.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Lab-module workflow data for one {@code lab_order} row (booking, QR token, who handles it). */
@Entity
@Table(name = "lab_order_detail")
public class LabOrderDetail extends BaseEntity {

  @Column(name = "lab_order_id", nullable = false, unique = true)
  private UUID labOrderId;

  @Column(name = "booking_ref", nullable = false)
  private String bookingRef;

  @Column(name = "booked_by")
  private UUID bookedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "booked_via", nullable = false)
  private BookingChannel bookedVia;

  @Column(name = "scheduled_date")
  private LocalDate scheduledDate;

  @Column(name = "slot_label")
  private String slotLabel;

  @Column(name = "price")
  private BigDecimal price;

  @Column(name = "qr_token", nullable = false)
  private String qrToken;

  @Column(name = "assigned_tech_id")
  private UUID assignedTechId;

  @Column(name = "resulted_by")
  private UUID resultedBy;

  @Column(name = "resulted_at")
  private Instant resultedAt;

  @Column(name = "rejected_reason", columnDefinition = "TEXT")
  private String rejectedReason;

  public UUID getLabOrderId() {
    return labOrderId;
  }

  public void setLabOrderId(UUID labOrderId) {
    this.labOrderId = labOrderId;
  }

  public String getBookingRef() {
    return bookingRef;
  }

  public void setBookingRef(String bookingRef) {
    this.bookingRef = bookingRef;
  }

  public UUID getBookedBy() {
    return bookedBy;
  }

  public void setBookedBy(UUID bookedBy) {
    this.bookedBy = bookedBy;
  }

  public BookingChannel getBookedVia() {
    return bookedVia;
  }

  public void setBookedVia(BookingChannel bookedVia) {
    this.bookedVia = bookedVia;
  }

  public LocalDate getScheduledDate() {
    return scheduledDate;
  }

  public void setScheduledDate(LocalDate scheduledDate) {
    this.scheduledDate = scheduledDate;
  }

  public String getSlotLabel() {
    return slotLabel;
  }

  public void setSlotLabel(String slotLabel) {
    this.slotLabel = slotLabel;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public String getQrToken() {
    return qrToken;
  }

  public void setQrToken(String qrToken) {
    this.qrToken = qrToken;
  }

  public UUID getAssignedTechId() {
    return assignedTechId;
  }

  public void setAssignedTechId(UUID assignedTechId) {
    this.assignedTechId = assignedTechId;
  }

  public UUID getResultedBy() {
    return resultedBy;
  }

  public void setResultedBy(UUID resultedBy) {
    this.resultedBy = resultedBy;
  }

  public Instant getResultedAt() {
    return resultedAt;
  }

  public void setResultedAt(Instant resultedAt) {
    this.resultedAt = resultedAt;
  }

  public String getRejectedReason() {
    return rejectedReason;
  }

  public void setRejectedReason(String rejectedReason) {
    this.rejectedReason = rejectedReason;
  }
}
