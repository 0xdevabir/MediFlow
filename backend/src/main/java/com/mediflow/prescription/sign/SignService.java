package com.mediflow.prescription.sign;

import com.mediflow.prescription.domain.GateStatus;
import com.mediflow.prescription.domain.ItemSource;
import com.mediflow.prescription.domain.RxFlagAck;
import com.mediflow.prescription.domain.RxItemMeta;
import com.mediflow.prescription.domain.RxPrescriptionMeta;
import com.mediflow.prescription.domain.RxSafetyFlag;
import com.mediflow.prescription.draft.DraftService;
import com.mediflow.prescription.repo.RxFlagAckRepository;
import com.mediflow.prescription.repo.RxItemMetaRepository;
import com.mediflow.prescription.repo.RxItemQueries;
import com.mediflow.prescription.repo.RxPrescriptionMetaRepository;
import com.mediflow.prescription.repo.RxPrescriptionQueries;
import com.mediflow.prescription.repo.RxSafetyFlagRepository;
import com.mediflow.prescription.safety.SafetyEngine;
import com.mediflow.prescription.support.RxAccess;
import com.mediflow.prescription.support.RxExceptions.LicenceRequiredException;
import com.mediflow.prescription.support.RxExceptions.SignBlockedException;
import com.mediflow.prescription.support.RxExceptions.ValidationException;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionItem;
import com.mediflow.shared.domain.PrescriptionStatus;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignService {

  private final RxAccess access;
  private final DraftService drafts;
  private final RxPrescriptionQueries prescriptions;
  private final RxPrescriptionMetaRepository metas;
  private final RxItemQueries items;
  private final RxItemMetaRepository itemMetas;
  private final RxSafetyFlagRepository flags;
  private final RxFlagAckRepository acks;
  private final RxMfaStepUp mfa;
  private final ApplicationEventPublisher events;
  private final AuditPort audit;
  private final ClockPort clock;

  public SignService(
      RxAccess access,
      DraftService drafts,
      RxPrescriptionQueries prescriptions,
      RxPrescriptionMetaRepository metas,
      RxItemQueries items,
      RxItemMetaRepository itemMetas,
      RxSafetyFlagRepository flags,
      RxFlagAckRepository acks,
      RxMfaStepUp mfa,
      ApplicationEventPublisher events,
      AuditPort audit,
      ClockPort clock) {
    this.access = access;
    this.drafts = drafts;
    this.prescriptions = prescriptions;
    this.metas = metas;
    this.items = items;
    this.itemMetas = itemMetas;
    this.flags = flags;
    this.acks = acks;
    this.mfa = mfa;
    this.events = events;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  @PreAuthorize("hasRole('DOCTOR')")
  public Prescription sign(UUID prescriptionId) {
    return sign(prescriptionId, null);
  }

  @Transactional
  @PreAuthorize("hasRole('DOCTOR')")
  public Prescription sign(UUID prescriptionId, LocalDate followUpDate) {
    Prescription rx = drafts.requireDraft(prescriptionId);
    UUID doctor = access.requireDoctorUserId();
    access.requireCanPrescribe(rx.getPatientId(), rx.getEncounterId());

    Practitioner practitioner =
        access
            .practitionerFor(doctor)
            .orElseThrow(LicenceRequiredException::new);
    if (practitioner.getLicenceNo() == null || practitioner.getLicenceNo().isBlank()) {
      throw new LicenceRequiredException();
    }

    mfa.requireFreshMfa();

    List<PrescriptionItem> lineItems = items.findByPrescriptionIdOrderByCreatedAtAsc(prescriptionId);
    if (lineItems.isEmpty()) {
      throw new SignBlockedException("No items to sign");
    }

    List<RxSafetyFlag> safetyFlags = flags.findByPrescriptionId(prescriptionId);
    List<RxFlagAck> ackRows = acks.findByPrescriptionId(prescriptionId);
    Set<String> acked =
        new HashSet<>();
    for (RxFlagAck a : ackRows) {
      // Interaction overrides require second confirmation
      boolean needsSecond =
          safetyFlags.stream()
              .anyMatch(
                  f ->
                      f.getFingerprint().equals(a.getFingerprint())
                          && f.isOverridable()
                          && f.getSeverity() == com.mediflow.shared.domain.ConflictSeverity.CRITICAL);
      if (!needsSecond || a.isSecondConfirmed()) {
        acked.add(a.getFingerprint());
      }
    }

    var findings =
        safetyFlags.stream()
            .map(
                f ->
                    com.mediflow.prescription.safety.SafetyFinding.of(
                        f.getItemId(),
                        f.getCheckType(),
                        f.getSeverity(),
                        f.getFingerprint(),
                        f.getMessageKey(),
                        f.getDetail(),
                        f.isOverridable()))
            .toList();
    if (SafetyEngine.hasUnresolvedCritical(findings, acked)) {
      throw new SignBlockedException("Unresolved CRITICAL safety flags");
    }

    for (PrescriptionItem item : lineItems) {
      RxItemMeta meta =
          itemMetas
              .findByItemId(item.getId())
              .orElseThrow(() -> new SignBlockedException("Missing item meta"));
      if (meta.getGateStatus() == GateStatus.HARD_STOP) {
        throw new SignBlockedException("HARD_STOP item present");
      }
      if (meta.getGateStatus() == GateStatus.NEEDS_REVIEW
          || meta.getGateStatus() == GateStatus.UNKNOWN) {
        if (!meta.isResolved()) {
          throw new SignBlockedException("Unresolved NEEDS_REVIEW/UNKNOWN item");
        }
      }
      if (item.getGenericId() == null) {
        throw new SignBlockedException("Item without known generic");
      }
      // KPI: acceptance vs correction for AI items
      if (meta.getSource() == ItemSource.AI) {
        boolean corrected =
            !Objects.equals(nullToEmpty(meta.getAiDose()), nullToEmpty(item.getDose()))
                || !Objects.equals(nullToEmpty(meta.getAiFrequency()), nullToEmpty(item.getFrequency()))
                || (meta.getAiBrand() != null
                    && item.getRawText() != null
                    && !meta.getAiBrand().equalsIgnoreCase(item.getRawText()));
        meta.setAiOutcome(corrected ? "CORRECTED" : "ACCEPTED");
        itemMetas.save(meta);
      }
    }

    // Warnings must be acked if present
    for (RxSafetyFlag f : safetyFlags) {
      if (f.getSeverity() == com.mediflow.shared.domain.ConflictSeverity.WARNING
          && f.isOverridable()
          && !acked.contains(f.getFingerprint())) {
        throw new SignBlockedException("Unacknowledged WARNING: " + f.getMessageKey());
      }
    }

    rx.setStatus(PrescriptionStatus.SIGNED);
    rx.setSignedBy(doctor);
    rx.setSignedAt(clock.now());
    prescriptions.save(rx);

    events.publishEvent(
        new DomainEvents.PrescriptionSigned(rx.getId(), rx.getPatientId(), doctor, clock.now()));
    audit.record(
        doctor,
        rx.getPatientId(),
        "RX_SIGN",
        "Prescription",
        rx.getId(),
        Map.of("licence", practitioner.getLicenceNo()));
    RxPrescriptionMeta rxMeta = metas.findByPrescriptionId(prescriptionId).orElse(null);
    LocalDate effectiveFollowUp = followUpDate;
    if (effectiveFollowUp == null && rxMeta != null) {
      effectiveFollowUp = rxMeta.getFollowUpDate();
    }
    if (effectiveFollowUp != null) {
      if (rxMeta != null) {
        rxMeta.setFollowUpDate(effectiveFollowUp);
        metas.save(rxMeta);
      }
      events.publishEvent(
          new DomainEvents.FollowUpScheduled(
              rx.getId(), rx.getPatientId(), effectiveFollowUp, clock.now()));
    }
    return rx;
  }

  @Transactional
  @PreAuthorize("hasRole('DOCTOR')")
  public Prescription createAddendum(UUID signedPrescriptionId) {
    Prescription original =
        prescriptions
            .findById(signedPrescriptionId)
            .orElseThrow(() -> new ValidationException("rx.error.not_found", "not found"));
    if (original.getStatus() != PrescriptionStatus.SIGNED
        && original.getStatus() != PrescriptionStatus.AMENDED) {
      throw new ValidationException("rx.error.not_signed", "Only signed Rx can be amended");
    }
    UUID doctor = access.requireDoctorUserId();
    access.requireCanPrescribe(original.getPatientId(), original.getEncounterId());

    Prescription addendum = new Prescription();
    addendum.setEncounterId(original.getEncounterId());
    addendum.setPatientId(original.getPatientId());
    addendum.setStatus(PrescriptionStatus.DRAFT);
    addendum = prescriptions.save(addendum);

    RxPrescriptionMeta meta = new RxPrescriptionMeta();
    meta.setPrescriptionId(addendum.getId());
    meta.setDoctorUserId(doctor);
    meta.setInputMode(com.mediflow.prescription.domain.RxInputMode.MANUAL);
    meta.setAmendsPrescriptionId(original.getId());
    metas.save(meta);

    if (original.getStatus() == PrescriptionStatus.SIGNED) {
      original.setStatus(PrescriptionStatus.AMENDED);
      prescriptions.save(original);
      events.publishEvent(
          new DomainEvents.PrescriptionAmended(
              original.getId(), original.getPatientId(), clock.now()));
    }
    audit.record(
        doctor,
        original.getPatientId(),
        "RX_ADDENDUM",
        "Prescription",
        addendum.getId(),
        Map.of("amends", signedPrescriptionId.toString()));
    return addendum;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
