package com.mediflow.prescription.web;

import com.mediflow.prescription.ai.AiGatewayService;
import com.mediflow.prescription.domain.RxInputMode;
import com.mediflow.prescription.domain.RxItemMeta;
import com.mediflow.prescription.domain.RxPrescriptionMeta;
import com.mediflow.prescription.domain.RxSafetyFlag;
import com.mediflow.prescription.draft.DraftInbox;
import com.mediflow.prescription.draft.DraftService;
import com.mediflow.prescription.safety.RxDrugDictionary;
import com.mediflow.prescription.repo.RxItemMetaRepository;
import com.mediflow.prescription.repo.RxSafetyFlagRepository;
import com.mediflow.prescription.sign.RevokeService;
import com.mediflow.prescription.sign.SignService;
import com.mediflow.prescription.support.RxAccess;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionItem;
import com.mediflow.shared.domain.PrescriptionStatus;
import com.mediflow.shared.web.CurrentUser;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/prescriptions")
public class PrescriptionController {

  private final DraftService drafts;
  private final DraftInbox inbox;
  private final SignService sign;
  private final RevokeService revoke;
  private final AiGatewayService ai;
  private final RxItemMetaRepository itemMetas;
  private final RxSafetyFlagRepository flags;
  private final RxAccess access;
  private final CurrentUser currentUser;
  private final RxDrugDictionary dictionary;

  public PrescriptionController(
      DraftService drafts,
      DraftInbox inbox,
      SignService sign,
      RevokeService revoke,
      AiGatewayService ai,
      RxItemMetaRepository itemMetas,
      RxSafetyFlagRepository flags,
      RxAccess access,
      CurrentUser currentUser,
      RxDrugDictionary dictionary) {
    this.drafts = drafts;
    this.inbox = inbox;
    this.sign = sign;
    this.revoke = revoke;
    this.ai = ai;
    this.itemMetas = itemMetas;
    this.flags = flags;
    this.access = access;
    this.currentUser = currentUser;
    this.dictionary = dictionary;
  }

  /**
   * List drafts for the signed-in doctor. When {@code encounterId} is present (consultation deep
   * link), open the existing draft or the new-draft form — never leave doctors typing a raw UUID.
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('DOCTOR','PHARMACIST','ADMIN','PATIENT')")
  public String index(@RequestParam(required = false) UUID encounterId, Model model) {
    if (encounterId != null && currentUser.hasRole("DOCTOR")) {
      DraftInbox.EncounterContext ctx = inbox.encounterContext(encounterId);
      access.requireCanPrescribe(ctx.patientId(), encounterId);
      Optional<Prescription> open = inbox.openDraftForEncounter(encounterId);
      if (open.isPresent()) {
        return "redirect:/prescriptions/" + open.get().getId();
      }
      return "redirect:/prescriptions/new?encounterId=" + encounterId;
    }

    model.addAttribute("pageTitleKey", "rx.title.list");
    if (currentUser.hasRole("DOCTOR")) {
      UUID doctor = access.requireDoctorUserId();
      List<DraftInbox.DraftRow> draftsNeedingReview = inbox.draftsNeedingReview(doctor, 25);
      model.addAttribute("drafts", draftsNeedingReview);
    } else {
      model.addAttribute("drafts", List.of());
    }
    return "prescription/list";
  }

  @GetMapping("/new")
  @PreAuthorize("hasRole('DOCTOR')")
  public String newForm(@RequestParam UUID encounterId, Model model) {
    DraftInbox.EncounterContext ctx = inbox.encounterContext(encounterId);
    access.requireCanPrescribe(ctx.patientId(), encounterId);
    model.addAttribute("pageTitleKey", "rx.title.new");
    model.addAttribute("encounterId", encounterId);
    model.addAttribute("encounter", ctx);
    return "prescription/new";
  }

  @PostMapping
  @PreAuthorize("hasRole('DOCTOR')")
  public String create(
      @RequestParam UUID encounterId,
      @RequestParam RxInputMode mode,
      @RequestParam(required = false) String typedText,
      @RequestParam(required = false) MultipartFile photo,
      RedirectAttributes flash)
      throws IOException {
    Prescription rx;
    if (mode == RxInputMode.PHOTO && photo != null && !photo.isEmpty()) {
      rx =
          drafts.createDraft(
              encounterId,
              mode,
              null,
              photo.getInputStream(),
              photo.getSize(),
              photo.getContentType());
    } else {
      rx = drafts.createDraft(encounterId, mode, typedText, null, 0, null);
    }
    flash.addFlashAttribute("successKey", "rx.flash.draft_created");
    return "redirect:/prescriptions/" + rx.getId();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('DOCTOR','PHARMACIST','ADMIN','PATIENT')")
  public String review(@PathVariable UUID id, Model model) {
    Prescription rx = drafts.requirePrescription(id);
    if (currentUser.hasRole("DOCTOR")) {
      access.requireCanPrescribe(rx.getPatientId(), rx.getEncounterId());
    }
    RxPrescriptionMeta meta = drafts.requireMeta(id);
    List<PrescriptionItem> lineItems = drafts.listItems(id);
    Map<UUID, RxItemMeta> metaByItem = new HashMap<>();
    for (PrescriptionItem item : lineItems) {
      itemMetas.findByItemId(item.getId()).ifPresent(m -> metaByItem.put(item.getId(), m));
    }
    List<RxSafetyFlag> safetyFlags = flags.findByPrescriptionId(id);
    model.addAttribute("pageTitleKey", "rx.title.review");
    model.addAttribute("rx", rx);
    model.addAttribute("meta", meta);
    model.addAttribute("items", lineItems);
    model.addAttribute("itemMetas", metaByItem);
    model.addAttribute("flags", safetyFlags);
    model.addAttribute("aiHealthy", ai.isHealthy());
    model.addAttribute("isDraft", rx.getStatus() == PrescriptionStatus.DRAFT);
    return "prescription/review";
  }

  @PostMapping("/{id}/items")
  @PreAuthorize("hasRole('DOCTOR')")
  public String addItem(
      @PathVariable UUID id,
      @RequestParam(required = false) String brandQuery,
      @RequestParam(required = false) UUID brandId,
      @RequestParam String dose,
      @RequestParam(required = false) String route,
      @RequestParam String frequency,
      @RequestParam(required = false) String duration,
      RedirectAttributes flash) {
    drafts.addManualItem(id, brandQuery, brandId, dose, route, frequency, duration);
    flash.addFlashAttribute("successKey", "rx.flash.item_added");
    return "redirect:/prescriptions/" + id;
  }

  @PostMapping("/{id}/items/{itemId}/confirm")
  @PreAuthorize("hasRole('DOCTOR')")
  public String confirm(@PathVariable UUID id, @PathVariable UUID itemId, RedirectAttributes flash) {
    drafts.confirmItem(id, itemId);
    flash.addFlashAttribute("successKey", "rx.flash.item_confirmed");
    return "redirect:/prescriptions/" + id;
  }

  @PostMapping("/{id}/items/{itemId}/update")
  @PreAuthorize("hasRole('DOCTOR')")
  public String updateItem(
      @PathVariable UUID id,
      @PathVariable UUID itemId,
      @RequestParam(required = false) UUID brandId,
      @RequestParam(required = false) String brandQuery,
      @RequestParam(required = false) String dose,
      @RequestParam(required = false) String route,
      @RequestParam(required = false) String frequency,
      @RequestParam(required = false) String duration,
      RedirectAttributes flash) {
    drafts.updateItem(id, itemId, brandId, brandQuery, dose, route, frequency, duration);
    flash.addFlashAttribute("successKey", "rx.flash.item_updated");
    return "redirect:/prescriptions/" + id;
  }

  @PostMapping("/{id}/items/{itemId}/delete")
  @PreAuthorize("hasRole('DOCTOR')")
  public String deleteItem(
      @PathVariable UUID id, @PathVariable UUID itemId, RedirectAttributes flash) {
    drafts.deleteItem(id, itemId);
    flash.addFlashAttribute("successKey", "rx.flash.item_deleted");
    return "redirect:/prescriptions/" + id;
  }

  @PostMapping("/{id}/flags/ack")
  @PreAuthorize("hasRole('DOCTOR')")
  public String ackFlag(
      @PathVariable UUID id,
      @RequestParam UUID itemId,
      @RequestParam String fingerprint,
      @RequestParam String reason,
      @RequestParam(defaultValue = "false") boolean secondConfirm,
      RedirectAttributes flash) {
    drafts.acknowledgeFlag(id, itemId, fingerprint, reason, secondConfirm);
    flash.addFlashAttribute("successKey", "rx.flash.flag_acked");
    return "redirect:/prescriptions/" + id;
  }

  @PostMapping("/{id}/sign")
  @PreAuthorize("hasRole('DOCTOR')")
  public String signRx(
      @PathVariable UUID id,
      @RequestParam(required = false) java.time.LocalDate followUpDate,
      RedirectAttributes flash) {
    sign.sign(id, followUpDate);
    flash.addFlashAttribute("successKey", "rx.flash.signed");
    return "redirect:/prescriptions/" + id + "/copy";
  }

  @PostMapping("/{id}/revoke")
  @PreAuthorize("hasRole('DOCTOR')")
  public String revoke(
      @PathVariable UUID id, @RequestParam String reason, RedirectAttributes flash) {
    revoke.revoke(id, reason);
    flash.addFlashAttribute("successKey", "rx.flash.revoked");
    return "redirect:/prescriptions/" + id;
  }

  @PostMapping("/{id}/addendum")
  @PreAuthorize("hasRole('DOCTOR')")
  public String addendum(@PathVariable UUID id, RedirectAttributes flash) {
    Prescription addendum = sign.createAddendum(id);
    flash.addFlashAttribute("successKey", "rx.flash.addendum");
    return "redirect:/prescriptions/" + addendum.getId();
  }

  @GetMapping("/{id}/copy")
  @PreAuthorize("hasAnyRole('DOCTOR','PHARMACIST','PATIENT','ADMIN')")
  public String patientCopy(@PathVariable UUID id, Model model) {
    Prescription rx = drafts.requirePrescription(id);
    if (rx.getStatus() == PrescriptionStatus.DRAFT) {
      return "redirect:/prescriptions/" + id;
    }
    RxPrescriptionMeta meta = drafts.requireMeta(id);
    List<PrescriptionItem> lineItems = drafts.listItems(id);
    Map<UUID, RxItemMeta> metaByItem = new HashMap<>();
    for (PrescriptionItem item : lineItems) {
      itemMetas.findByItemId(item.getId()).ifPresent(m -> metaByItem.put(item.getId(), m));
    }
    model.addAttribute("pageTitleKey", "rx.title.copy");
    model.addAttribute("rx", rx);
    model.addAttribute("meta", meta);
    model.addAttribute("items", lineItems);
    model.addAttribute("itemMetas", metaByItem);
    Map<UUID, String> generics = new HashMap<>();
    for (PrescriptionItem item : lineItems) {
      if (item.getGenericId() != null) {
        dictionary
            .generic(item.getGenericId())
            .ifPresent(g -> generics.put(item.getId(), g.name()));
      } else if (metaByItem.containsKey(item.getId())
          && metaByItem.get(item.getId()).getAiGeneric() != null) {
        generics.put(item.getId(), metaByItem.get(item.getId()).getAiGeneric());
      }
    }
    model.addAttribute("generics", generics);
    model.addAttribute("qrPayload", "mediflow:rx:" + id);
    return "prescription/copy";
  }

  @GetMapping("/{id}/photo")
  @PreAuthorize("hasAnyRole('DOCTOR','PHARMACIST','ADMIN','PATIENT')")
  public org.springframework.http.ResponseEntity<org.springframework.core.io.InputStreamResource> photo(
      @PathVariable UUID id) {
    Prescription rx = drafts.requirePrescription(id);
    if (currentUser.hasRole("DOCTOR")) {
      access.requireCanPrescribe(rx.getPatientId(), rx.getEncounterId());
    }
    return drafts
        .photo(id)
        .map(
            blob ->
                org.springframework.http.ResponseEntity.ok()
                    .header(
                        org.springframework.http.HttpHeaders.CONTENT_TYPE, blob.contentType())
                    .body(new org.springframework.core.io.InputStreamResource(blob.stream())))
        .orElse(org.springframework.http.ResponseEntity.notFound().build());
  }
}
