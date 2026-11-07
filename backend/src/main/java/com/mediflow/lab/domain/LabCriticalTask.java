package com.mediflow.lab.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** "Phone the patient" task created for the verifier when a verified report has a critical value. */
@Entity
@Table(name = "lab_critical_task")
public class LabCriticalTask extends BaseEntity {

  public enum Status {
    OPEN,
    DONE
  }

  @Column(name = "report_id", nullable = false, unique = true)
  private UUID reportId;

  @Column(name = "lab_order_id", nullable = false)
  private UUID labOrderId;

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "assignee_id", nullable = false)
  private UUID assigneeId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status;

  @Column(name = "done_by")
  private UUID doneBy;

  @Column(name = "done_at")
  private Instant doneAt;

  @Column(name = "outcome_note", columnDefinition = "TEXT")
  private String outcomeNote;

  public UUID getReportId() {
    return reportId;
  }

  public void setReportId(UUID reportId) {
    this.reportId = reportId;
  }

  public UUID getLabOrderId() {
    return labOrderId;
  }

  public void setLabOrderId(UUID labOrderId) {
    this.labOrderId = labOrderId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public UUID getAssigneeId() {
    return assigneeId;
  }

  public void setAssigneeId(UUID assigneeId) {
    this.assigneeId = assigneeId;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public UUID getDoneBy() {
    return doneBy;
  }

  public void setDoneBy(UUID doneBy) {
    this.doneBy = doneBy;
  }

  public Instant getDoneAt() {
    return doneAt;
  }

  public void setDoneAt(Instant doneAt) {
    this.doneAt = doneAt;
  }

  public String getOutcomeNote() {
    return outcomeNote;
  }

  public void setOutcomeNote(String outcomeNote) {
    this.outcomeNote = outcomeNote;
  }
}
