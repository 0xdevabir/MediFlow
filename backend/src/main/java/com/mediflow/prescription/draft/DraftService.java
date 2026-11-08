package com.mediflow.prescription.draft;

import com.mediflow.prescription.domain.GateStatus;
import com.mediflow.prescription.domain.ItemSource;
import com.mediflow.prescription.domain.RxFlagAck;
import com.mediflow.prescription.domain.RxInputMode;
import com.mediflow.prescription.domain.RxItemMeta;
import com.mediflow.prescription.domain.RxPrescriptionMeta;
import com.mediflow.prescription.repo.RxFlagAckRepository;
import com.mediflow.prescription.repo.RxItemMetaRepository;
import com.mediflow.prescription.repo.RxItemQueries;
import com.mediflow.prescription.repo.RxPrescriptionMetaRepository;
import com.mediflow.prescription.repo.RxPrescriptionQueries;
import com.mediflow.prescription.safety.ConfidenceGate;
import com.mediflow.prescription.safety.RxDrugDictionary;
import com.mediflow.prescription.safety.RxDrugDictionary.BrandInfo;
import com.mediflow.prescription.safety.SafetyEvaluationService;
import com.mediflow.prescription.safety.ThresholdSettings;
import com.mediflow.prescription.support.RxAccess;
import com.mediflow.prescription.support.RxExceptions.NotDraftException;
import com.mediflow.prescription.support.RxExceptions.ValidationException;
import com.mediflow.shared.domain.AiFlagOverride;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionItem;
import com.mediflow.shared.domain.PrescriptionStatus;
import com.mediflow.shared.domain.ReviewStatus;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.ObjectStoragePort;
import com.mediflow.shared.repo.AiFlagOverrideRepository;
import com.mediflow.shared.repo.EncounterRepository;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftService {

  private final RxAccess access;
  private final EncounterRepository encounters;
  private final RxPrescriptionQueries prescriptions;
  private final RxPrescriptionMetaRepository metas;
  private final RxItemQueries items;
  private final RxItemMetaRepository itemMetas;
  private final RxFlagAckRepository acks;
  private final AiFlagOverrideRepository overrides;
  private final ObjectStoragePort storage;
  private final RxDrugDictionary dictionary;
  private final SafetyEvaluationService safety;
  private final ThresholdSettings thresholds;
  private final AuditPort audit;
  private final ClockPort clock;

  public DraftService(
      RxAccess access,
      EncounterRepository encounters,
      RxPrescriptionQueries prescriptions,
      RxPrescriptionMetaRepository metas,
      RxItemQueries items,
      RxItemMetaRepository itemMetas,
      RxFlagAckRepository acks,
      AiFlagOverrideRepository overrides,
      ObjectStoragePort storage,
      RxDrugDictionary dictionary,
      SafetyEvaluationService safety,
      ThresholdSettings thresholds,
      AuditPort audit,
      ClockPort clock) {
    this.access = access;
    this.encounters = encounters;
    this.prescriptions = prescriptions;
    this.metas = metas;
    this.items = items;
    this.itemMetas = itemMetas;
    this.acks = acks;
    this.overrides = overrides;
    this.storage = storage;
    this.dictionary = dictionary;
    this.safety = safety;
    this.thresholds = thresholds;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  @PreAuthorize("hasRole('DOCTOR')")
  public Prescription createDraft(
      UUID encounterId, RxInputMode mode, String typedText, InputStream photo, long photoLen, String photoType) {
    Encounter encounter =
        encounters.findById(encounterId).orElseThrow(() -> new ValidationException("rx.error.encounter", "not found"));
    UUID doctorUserId = access.requireDoctorUserId();
    access.requireCanPrescribe(encounter.getPatientId(), encounterId);

    Prescription rx = new Prescription();
    rx.setEncounterId(encounterId);
    rx.setPatientId(encounter.getPatientId());
    rx.setStatus(PrescriptionStatus.DRAFT);
    rx = prescriptions.save(rx);

    RxPrescriptionMeta meta = new RxPrescriptionMeta();
    meta.setPrescriptionId(rx.getId());
    meta.setDoctorUserId(doctorUserId);
    meta.setInputMode(mode == null ? RxInputMode.MANUAL : mode);
    meta.setTypedText(typedText);
    if (mode == RxInputMode.PHOTO && photo != null) {
      String key = "rx/" + rx.getId() + "/photo-" + UUID.randomUUID();
      storage.put(key, photo, photoLen, photoType == null ? "image/jpeg" : photoType);
      meta.setPhotoObjectKey(key);
      meta.setPhotoContentType(photoType);
    }
    metas.save(meta);

    audit.record(
        doctorUserId,
        encounter.getPatientId(),
        "RX_DRAFT_CREATE",
        "Prescription",
        rx.getId(),
        Map.of("mode", meta.getInputMode().name()));
    return rx;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('DOCTOR') or hasRole('PHARMACIST') or hasRole('PATIENT') or hasRole('ADMIN')")
  public Prescription requirePrescription(UUID id) {
    return prescriptions.findById(id).orElseThrow(() -> new ValidationException("rx.error.not_found", "not found"));
  }

  @Transactional
  @PreAuthorize("hasRole('DOCTOR')")
  public PrescriptionItem addManualItem(
      UUID prescriptionId,
      String brandQuery,
      UUID brandId,
      String dose,
      String route,
      String frequency,
      String duration) {
    Prescription rx = requireDraft(prescriptionId);
    access.requireCanPrescribe(rx.getPatientId(), rx.getEncounterId());
    BrandInfo brand = resolveBrand(brandId, brandQuery);
    PrescriptionItem item = new PrescriptionItem();
    item.setPrescriptionId(prescriptionId);
    if (brand != null) {
      item.setBrandId(brand.id());
      item.setGenericId(brand.genericId());
      item.setRawText(brand.name());
    } else {
      item.setRawText(brandQuery);
      // unknown: leave generic null (V350); JPA column annotation still says non-null — see requests
    }
    item.setDose(dose);
    item.setRoute(route);
    item.setFrequency(frequency);
    item.setDuration(duration);
    item.setReviewStatus(ReviewStatus.PENDING);
    item = items.save(item);

    RxItemMeta meta = new RxItemMeta();
    meta.setItemId(item.getId());
    meta.setSource(ItemSource.MANUAL);
    meta.setGateStatus(brand == null ? GateStatus.UNKNOWN : GateStatus.MANUAL);
    meta.setResolved(false);
    meta.setPosition(items.findByPrescriptionIdOrderByCreatedAtAsc(prescriptionId).size());
    itemMetas.save(meta);

    safety.reevaluate(prescriptionId);
    audit.record(
        access.requireDoctorUserId(),
        rx.getPatientId(),
        "RX_ITEM_ADD",
        "PrescriptionItem",
        item.getId(),
        Map.of("source", "MANUAL"));
    return item;
  }

  @Transactional
  @PreAuthorize("hasRole('DOCTOR')")
  public PrescriptionItem updateItem(
      UUID prescriptionId,
      UUID itemId,
      UUID brandId,
      String brandQuery,
      String dose,
      String route,
      String frequency,
      String duration) {
    Prescription rx = requireDraft(prescriptionId);
    access.requireCanPrescribe(rx.getPatientId(), rx.getEncounterId());
    PrescriptionItem item =
        items.findById(itemId).orElseThrow(() -> new ValidationException("rx.error.item", "not found"));
    if (!item.getPrescriptionId().equals(prescriptionId)) {
      throw new ValidationException("rx.error.item", "mismatch");
    }
    BrandInfo brand = resolveBrand(brandId, brandQuery);
    if (brand != null) {
      item.setBrandId(brand.id());
      item.setGenericId(brand.genericId());
      item.setRawText(brand.name());
    } else if (brandQuery != null && !brandQuery.isBlank()) {
      item.setBrandId(null);
      item.setGenericId(null);
      item.setRawText(brandQuery);
    }
    if (dose != null) {
      item.setDose(dose);
    }
    if (route != null) {
      item.setRoute(route);
    }
    if (frequency != null) {
      item.setFrequency(frequency);
    }
    if (duration != null) {
      item.setDuration(duration);
    }
    items.save(item);

    RxItemMeta meta =
        itemMetas
            .findByItemId(itemId)
            .orElseThrow(() -> new ValidationException("rx.error.item", "meta missing"));
    if (meta.getSource() == ItemSource.AI) {
      // correcting an AI guess → mark resolved once doctor saves
      meta.setResolved(true);
      meta.setResolvedBy(access.requireDoctorUserId());
      meta.setResolvedAt(clock.now());
      meta.setGateStatus(brand == null ? GateStatus.UNKNOWN : GateStatus.NEEDS_REVIEW);
    } else {
      meta.setGateStatus(brand == null ? GateStatus.UNKNOWN : GateStatus.MANUAL);
    }
    itemMetas.save(meta);
    safety.reevaluate(prescriptionId);
    return item;
  }

  @Transactional
  @PreAuthorize("hasRole('DOCTOR')")
  public void confirmItem(UUID prescriptionId, UUID itemId) {
    Prescription rx = requireDraft(prescriptionId);
    access.requireCanPrescribe(rx.getPatientId(), rx.getEncounterId());
    PrescriptionItem item =
        items.findById(itemId).orElseThrow(() -> new ValidationException("rx.error.item", "not found"));
    RxItemMeta meta =
        itemMetas
            .findByItemId(itemId)
            .orElseThrow(() -> new ValidationException("rx.error.item", "meta missing"));
    if (meta.getGateStatus() == GateStatus.UNKNOWN && item.getGenericId() == null) {
      throw new ValidationException("rx.error.unknown_unresolved", "Pick a known medicine first");
    }
    if (meta.getGateStatus() == GateStatus.HARD_STOP) {
      throw new ValidationException("rx.error.hard_stop", "Resolve critical conflict first");
    }
    meta.setResolved(true);
    meta.setResolvedBy(access.requireDoctorUserId());
    meta.setResolvedAt(clock.now());
    item.setReviewStatus(ReviewStatus.ACCEPTED);
    itemMetas.save(meta);
    items.save(item);
    audit.record(
        access.requireDoctorUserId(),
        rx.getPatientId(),
        "RX_ITEM_CONFIRM",
        "PrescriptionItem",
        itemId,
        Map.of());
  }

  @Transactional
  @PreAuthorize("hasRole('DOCTOR')")
  public void acknowledgeFlag(
      UUID prescriptionId, UUID itemId, String fingerprint, String reason, boolean secondConfirm) {
    Prescription rx = requireDraft(prescriptionId);
    access.requireCanPrescribe(rx.getPatientId(), rx.getEncounterId());
    if (reason == null || reason.trim().length() < 5) {
      throw new ValidationException("rx.error.override_reason", "Reason required (min 5 chars)");
    }
    UUID doctor = access.requireDoctorUserId();
    AiFlagOverride ov = new AiFlagOverride();
    ov.setPrescriptionItemId(itemId);
    ov.setOverriddenBy(doctor);
    ov.setReason(reason.trim());
    ov = overrides.save(ov);

    RxFlagAck ack =
        acks.findByItemIdAndFingerprint(itemId, fingerprint)
            .orElseGet(RxFlagAck::new);
    ack.setPrescriptionId(prescriptionId);
    ack.setItemId(itemId);
    ack.setFingerprint(fingerprint);
    ack.setOverrideId(ov.getId());
    ack.setAckedBy(doctor);
    ack.setAckedAt(clock.now());
    if (secondConfirm) {
      ack.setSecondConfirmed(true);
    }
    acks.save(ack);

    PrescriptionItem item = items.findById(itemId).orElseThrow();
    item.setReviewStatus(ReviewStatus.OVERRIDDEN);
    items.save(item);

    audit.record(
        doctor,
        rx.getPatientId(),
        "RX_FLAG_OVERRIDE",
        "PrescriptionItem",
        itemId,
        Map.of("fingerprint", fingerprint, "second", secondConfirm));
  }

  @Transactional
  @PreAuthorize("hasRole('DOCTOR')")
  public void deleteItem(UUID prescriptionId, UUID itemId) {
    Prescription rx = requireDraft(prescriptionId);
    access.requireCanPrescribe(rx.getPatientId(), rx.getEncounterId());
    PrescriptionItem item =
        items.findById(itemId).orElseThrow(() -> new ValidationException("rx.error.item", "not found"));
    itemMetas.findByItemId(itemId).ifPresent(itemMetas::delete);
    items.delete(item);
    safety.reevaluate(prescriptionId);
  }

  @Transactional(readOnly = true)
  public List<PrescriptionItem> listItems(UUID prescriptionId) {
    return items.findByPrescriptionIdOrderByCreatedAtAsc(prescriptionId);
  }

  @Transactional(readOnly = true)
  public RxPrescriptionMeta requireMeta(UUID prescriptionId) {
    return metas
        .findByPrescriptionId(prescriptionId)
        .orElseThrow(() -> new ValidationException("rx.error.not_found", "meta"));
  }

  public Prescription requireDraft(UUID prescriptionId) {
    Prescription rx = requirePrescription(prescriptionId);
    if (rx.getStatus() != PrescriptionStatus.DRAFT) {
      throw new NotDraftException();
    }
    return rx;
  }

  /** Apply one AI result line onto a new draft item (called by AI gateway). */
  @Transactional
  public PrescriptionItem applyAiItem(
      UUID prescriptionId,
      UUID aiJobId,
      String raw,
      String brandName,
      String genericName,
      String dose,
      String route,
      String frequency,
      String duration,
      BigDecimal confDrug,
      BigDecimal confDose,
      BigDecimal confFrequency,
      BigDecimal confRoute,
      BigDecimal confDuration,
      int position) {
    Prescription rx = requireDraft(prescriptionId);
    BrandInfo brand = null;
    if (brandName != null && !brandName.isBlank()) {
      List<BrandInfo> matches = dictionary.brandsByName(brandName);
      if (matches.size() == 1) {
        brand = matches.get(0);
      } else if (matches.size() > 1) {
        // prefer exact strength match later; take first for now
        brand = matches.get(0);
      }
    }
    boolean known = brand != null;
    GateStatus gate =
        ConfidenceGate.decide(
            confDrug,
            confDose,
            confFrequency,
            known,
            null,
            thresholds.confidenceThreshold());

    PrescriptionItem item = new PrescriptionItem();
    item.setPrescriptionId(prescriptionId);
    item.setRawText(raw);
    item.setDose(dose);
    item.setRoute(route);
    item.setFrequency(frequency);
    item.setDuration(duration);
    item.setConfidence(ConfidenceGate.itemConfidence(confDrug, confDose, confFrequency));
    item.setReviewStatus(ReviewStatus.PENDING);
    if (known && gate == GateStatus.PREFILLED) {
      item.setBrandId(brand.id());
      item.setGenericId(brand.genericId());
    } else if (known && gate == GateStatus.NEEDS_REVIEW) {
      // amber: store AI guess in meta; leave item fields partially filled for doctor
      item.setBrandId(brand.id());
      item.setGenericId(brand.genericId());
    }
    // UNKNOWN → blank brand/generic
    item = items.save(item);

    RxItemMeta meta = new RxItemMeta();
    meta.setItemId(item.getId());
    meta.setSource(ItemSource.AI);
    meta.setGateStatus(gate);
    meta.setResolved(false);
    meta.setConfDrug(confDrug);
    meta.setConfDose(confDose);
    meta.setConfFrequency(confFrequency);
    meta.setConfRoute(confRoute);
    meta.setConfDuration(confDuration);
    meta.setAiJobId(aiJobId);
    meta.setAiBrand(brandName);
    meta.setAiGeneric(genericName);
    meta.setAiDose(dose);
    meta.setAiRoute(route);
    meta.setAiFrequency(frequency);
    meta.setAiDuration(duration);
    meta.setAiGenericId(brand == null ? null : brand.genericId());
    meta.setPosition(position);
    itemMetas.save(meta);
    return item;
  }

  private BrandInfo resolveBrand(UUID brandId, String brandQuery) {
    if (brandId != null) {
      return dictionary.brand(brandId).orElse(null);
    }
    if (brandQuery == null || brandQuery.isBlank()) {
      return null;
    }
    List<BrandInfo> exact = dictionary.brandsByName(brandQuery);
    if (exact.size() == 1) {
      return exact.get(0);
    }
    List<BrandInfo> search = dictionary.searchBrands(brandQuery, 5);
    return search.size() == 1 ? search.get(0) : null;
  }

  public record PhotoBlob(InputStream stream, String contentType) {}

  @Transactional(readOnly = true)
  public Optional<PhotoBlob> photo(UUID prescriptionId) {
    requirePrescription(prescriptionId);
    RxPrescriptionMeta meta = requireMeta(prescriptionId);
    if (meta.getPhotoObjectKey() == null || meta.getPhotoObjectKey().isBlank()) {
      return Optional.empty();
    }
    return storage
        .get(meta.getPhotoObjectKey())
        .map(
            in ->
                new PhotoBlob(
                    in,
                    meta.getPhotoContentType() == null
                        ? "image/jpeg"
                        : meta.getPhotoContentType()));
  }

  @Transactional
  @PreAuthorize("hasRole('DOCTOR')")
  public void setFollowUpDate(UUID prescriptionId, LocalDate followUpDate) {
    Prescription rx = requireDraft(prescriptionId);
    access.requireCanPrescribe(rx.getPatientId(), rx.getEncounterId());
    RxPrescriptionMeta meta = requireMeta(prescriptionId);
    meta.setFollowUpDate(followUpDate);
    metas.save(meta);
  }
}
