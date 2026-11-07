package com.mediflow.notification.service;

import com.mediflow.notification.channel.Masking;
import com.mediflow.notification.domain.NotificationEventType;
import com.mediflow.notification.domain.Priority;
import com.mediflow.notification.domain.StaffCallEntry;
import com.mediflow.notification.domain.StaffCallStatus;
import com.mediflow.notification.repo.StaffCallEntryRepository;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.PatientRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Risk R7 staff phone-call list: notifications that could not be delivered electronically. */
@Service
public class StaffCallListService {

  public static final String STAFF_ROLES = "hasAnyRole('FRONT_DESK','NURSE','ER','ADMIN')";

  /** Row for the call-list page (phone shown in full: staff must dial it; the view is audited). */
  public record CallRow(StaffCallEntry entry, String patientName, String phone, String maskedPhone) {}

  private final StaffCallEntryRepository entries;
  private final PatientRepository patients;
  private final AuditPort audit;
  private final ClockPort clock;

  public StaffCallListService(
      StaffCallEntryRepository entries, PatientRepository patients, AuditPort audit, ClockPort clock) {
    this.entries = entries;
    this.patients = patients;
    this.audit = audit;
    this.clock = clock;
  }

  /** Adds an entry, or refreshes the open entry of the same notification chain. */
  @Transactional
  public StaffCallEntry addOrUpdate(
      UUID rootNotificationId,
      UUID patientId,
      UUID recipientUserId,
      NotificationEventType type,
      Priority priority,
      String reason,
      String message) {
    StaffCallEntry e =
        Optional.ofNullable(rootNotificationId)
            .flatMap(id -> entries.findFirstByRootNotificationIdAndStatus(id, StaffCallStatus.OPEN))
            .orElseGet(StaffCallEntry::new);
    e.setRootNotificationId(rootNotificationId);
    e.setPatientId(patientId);
    e.setRecipientUserId(recipientUserId);
    e.setEventType(type);
    if (e.getPriority() != Priority.HIGH) {
      e.setPriority(priority);
    }
    e.setReason(reason);
    e.setMessage(message);
    return entries.save(e);
  }

  @PreAuthorize(STAFF_ROLES)
  @Transactional(readOnly = true)
  public List<CallRow> openEntries(UUID actorUserId) {
    List<CallRow> rows =
        entries.findByStatusOrderByPriorityDescCreatedAtAsc(StaffCallStatus.OPEN).stream()
            .map(this::toRow)
            .toList();
    audit.record(
        actorUserId, null, "NOTIFICATION_CALL_LIST_VIEW", "StaffCallEntry", null, Map.of("count", rows.size()));
    return rows;
  }

  @PreAuthorize(STAFF_ROLES)
  @Transactional
  public void resolve(UUID entryId, UUID actorUserId) {
    StaffCallEntry e =
        entries.findById(entryId).orElseThrow(() -> new IllegalArgumentException("Unknown entry"));
    e.setStatus(StaffCallStatus.RESOLVED);
    e.setResolvedBy(actorUserId);
    e.setResolvedAt(clock.now());
    audit.record(
        actorUserId, e.getPatientId(), "NOTIFICATION_CALL_RESOLVED", "StaffCallEntry", entryId, Map.of());
  }

  @Transactional(readOnly = true)
  public long openCount() {
    return entries.countByStatus(StaffCallStatus.OPEN);
  }

  private CallRow toRow(StaffCallEntry e) {
    Optional<Patient> p = e.getPatientId() == null ? Optional.empty() : patients.findById(e.getPatientId());
    String phone = p.map(Patient::getPhone).orElse(null);
    return new CallRow(
        e,
        p.map(x -> x.getGivenName() + " " + x.getFamilyName()).orElse(""),
        phone,
        phone == null ? "" : Masking.phone(phone));
  }
}
