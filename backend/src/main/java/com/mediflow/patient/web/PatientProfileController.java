package com.mediflow.patient.web;

import com.mediflow.patient.domain.ContactChannel;
import com.mediflow.patient.domain.DocumentType;
import com.mediflow.patient.domain.HistoryCategory;
import com.mediflow.patient.service.ContactVerificationService;
import com.mediflow.patient.service.OtpIssuer;
import com.mediflow.patient.service.PatientAccessGuard;
import com.mediflow.patient.service.PatientClinicalHistoryService;
import com.mediflow.patient.service.PatientDashboardService;
import com.mediflow.patient.service.PatientDocumentService;
import com.mediflow.patient.service.PatientProfileService;
import com.mediflow.patient.service.PatientView;
import com.mediflow.patient.service.QrTokenService;
import com.mediflow.shared.port.AdherenceQueryPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/patients")
public class PatientProfileController {

  private final PatientProfileService profiles;
  private final PatientClinicalHistoryService clinical;
  private final PatientDocumentService documents;
  private final ContactVerificationService contacts;
  private final PatientAccessGuard access;
  private final QrTokenService qrTokens;
  private final PatientDashboardService dashboard;
  private final ObjectProvider<AdherenceQueryPort> adherence;
  private final CurrentUser currentUser;

  public PatientProfileController(
      PatientProfileService profiles,
      PatientClinicalHistoryService clinical,
      PatientDocumentService documents,
      ContactVerificationService contacts,
      PatientAccessGuard access,
      QrTokenService qrTokens,
      PatientDashboardService dashboard,
      ObjectProvider<AdherenceQueryPort> adherence,
      CurrentUser currentUser) {
    this.profiles = profiles;
    this.clinical = clinical;
    this.documents = documents;
    this.contacts = contacts;
    this.access = access;
    this.qrTokens = qrTokens;
    this.dashboard = dashboard;
    this.adherence = adherence;
    this.currentUser = currentUser;
  }

  /** Role landing: patient → dashboard; front desk / nurse → search. */
  @GetMapping({"", "/"})
  public String landing() {
    if (currentUser.hasRole("PATIENT")) {
      return "redirect:/patients/me/dashboard";
    }
    if (currentUser.hasRole("FRONT_DESK") || currentUser.hasRole("NURSE")) {
      return "redirect:/patients/search";
    }
    return "redirect:/";
  }

  @GetMapping("/me")
  public String me() {
    UUID id =
        access
            .ownPatientId()
            .orElseThrow(
                () -> new org.springframework.security.access.AccessDeniedException("not a patient"));
    return "redirect:/patients/me/dashboard";
  }

  @GetMapping("/me/dashboard")
  public String myDashboard(Model model) {
    UUID id =
        access
            .ownPatientId()
            .orElseThrow(
                () -> new org.springframework.security.access.AccessDeniedException("not a patient"));
    return dashboard(id, model);
  }

  @GetMapping("/{patientId}/dashboard")
  public String dashboard(@PathVariable UUID patientId, Model model) {
    PatientDashboardService.DashboardView view = dashboard.load(patientId);
    model.addAttribute("pageTitleKey", "patient.dashboard.title");
    model.addAttribute("patient", view.patient());
    model.addAttribute("pct", view.pct());
    model.addAttribute("adherencePercent", view.pct());
    model.addAttribute("adherenceConfigured", view.adherenceConfigured());
    model.addAttribute("nextVisit", view.nextVisit());
    model.addAttribute("qrDataUri", view.qrDataUri());
    model.addAttribute("qrExpiresAt", view.qrExpiresAt());
    model.addAttribute("isSelf", access.isSelf(patientId));
    return "patient/dashboard";
  }

  @GetMapping("/search")
  public String search(@RequestParam(value = "q", required = false) String q, Model model) {
    model.addAttribute("pageTitleKey", "patient.search.title");
    model.addAttribute("q", q);
    model.addAttribute("results", profiles.search(q));
    return "patient/search";
  }

  @GetMapping("/{patientId}")
  public String profile(@PathVariable UUID patientId, Model model) {
    PatientView view = profiles.get(patientId);
    model.addAttribute("pageTitleKey", "patient.profile.title");
    model.addAttribute("patient", view);
    model.addAttribute(
        "allergies",
        access.canReadClinicalHistory(patientId) ? clinical.listAllergies(patientId) : List.of());
    model.addAttribute(
        "history",
        access.canReadClinicalHistory(patientId) ? clinical.listHistory(patientId) : List.of());
    model.addAttribute(
        "documents",
        access.canAccessDocuments(patientId) ? documents.list(patientId) : List.of());
    model.addAttribute("canUpdateContact", access.canUpdateContact(patientId));
    model.addAttribute("canConfirmClinical", access.canConfirmClinicalHistory(patientId));
    model.addAttribute("canAccessDocuments", access.canAccessDocuments(patientId));
    model.addAttribute("isSelf", access.isSelf(patientId));
    QrTokenService.IssuedToken token = profiles.issueCardToken(patientId);
    model.addAttribute("qrDataUri", qrTokens.qrDataUri(token.token()));
    model.addAttribute("qrExpiresAt", token.expiresAt());
    if (access.isSelf(patientId)) {
      AdherenceQueryPort port = adherence.getIfAvailable();
      Optional<Integer> pct =
          port == null ? Optional.empty() : port.adherencePercent(patientId);
      model.addAttribute("pct", pct.orElse(null));
      model.addAttribute("adherencePercent", pct.orElse(null));
      model.addAttribute("adherenceConfigured", port != null);
    } else {
      model.addAttribute("pct", null);
      model.addAttribute("adherencePercent", null);
      model.addAttribute("adherenceConfigured", false);
    }
    return "patient/profile";
  }

  @GetMapping("/{patientId}/card")
  public String card(@PathVariable UUID patientId, Model model) {
    PatientView view = profiles.get(patientId);
    QrTokenService.IssuedToken token = profiles.issueCardToken(patientId);
    model.addAttribute("pageTitleKey", "patient.card.title");
    model.addAttribute("patient", view);
    model.addAttribute("qrDataUri", qrTokens.qrDataUri(token.token()));
    model.addAttribute("qrExpiresAt", token.expiresAt());
    return "patient/card";
  }

  @PostMapping("/{patientId}/contact")
  public String updateContact(
      @PathVariable UUID patientId,
      @RequestParam(required = false) String phone,
      @RequestParam(required = false) String email,
      @RequestParam(required = false) String address,
      RedirectAttributes flash) {
    profiles.updateContact(patientId, phone, email, address);
    flash.addFlashAttribute("successKey", "patient.contact.updated");
    return "redirect:/patients/" + patientId;
  }

  @PostMapping("/{patientId}/otp/send")
  public String sendOtp(
      @PathVariable UUID patientId, @RequestParam ContactChannel channel, RedirectAttributes flash) {
    contacts.resend(patientId, channel);
    flash.addFlashAttribute("successKey", "patient.otp.sent");
    return "redirect:/patients/" + patientId;
  }

  @PostMapping("/{patientId}/otp/verify")
  public String verifyOtp(
      @PathVariable UUID patientId,
      @RequestParam ContactChannel channel,
      @RequestParam String code,
      RedirectAttributes flash) {
    OtpIssuer.Outcome outcome = contacts.verify(patientId, channel, code);
    flash.addFlashAttribute(
        outcome == OtpIssuer.Outcome.VERIFIED ? "successKey" : "errorKey",
        outcome == OtpIssuer.Outcome.VERIFIED
            ? "patient.otp.verified"
            : "patient.otp." + outcome.name().toLowerCase());
    return "redirect:/patients/" + patientId;
  }

  @PostMapping("/{patientId}/demographics/request")
  public String requestChange(
      @PathVariable UUID patientId,
      @RequestParam(required = false) String newGivenName,
      @RequestParam(required = false) String newFamilyName,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate newDateOfBirth,
      @RequestParam(required = false) String reason,
      RedirectAttributes flash) {
    profiles.requestDemographicChange(
        patientId, newGivenName, newFamilyName, newDateOfBirth, reason);
    flash.addFlashAttribute("successKey", "patient.change.submitted");
    return "redirect:/patients/" + patientId;
  }

  @GetMapping("/changes")
  public String pendingChanges(Model model) {
    model.addAttribute("pageTitleKey", "patient.change.listTitle");
    model.addAttribute("requests", profiles.listPendingChanges());
    return "patient/changes";
  }

  @PostMapping("/changes/{requestId}")
  public String reviewChange(
      @PathVariable UUID requestId,
      @RequestParam boolean approve,
      @RequestParam(required = false) String note,
      RedirectAttributes flash) {
    var req = profiles.reviewDemographicChange(requestId, approve, note);
    flash.addFlashAttribute(
        "successKey", approve ? "patient.change.approved" : "patient.change.rejected");
    return "redirect:/patients/" + req.getPatientId();
  }

  @PostMapping("/{patientId}/allergies")
  public String addAllergy(
      @PathVariable UUID patientId,
      @RequestParam String substance,
      @RequestParam(required = false) String substanceCode,
      @RequestParam(required = false) String criticality,
      @RequestParam(required = false) String note,
      RedirectAttributes flash) {
    clinical.addAllergy(patientId, substance, substanceCode, criticality, note);
    flash.addFlashAttribute("successKey", "patient.allergy.added");
    return "redirect:/patients/" + patientId;
  }

  @PostMapping("/{patientId}/allergies/{allergyId}/confirm")
  public String confirmAllergy(
      @PathVariable UUID patientId, @PathVariable UUID allergyId, RedirectAttributes flash) {
    clinical.confirmAllergy(patientId, allergyId);
    flash.addFlashAttribute("successKey", "patient.allergy.confirmed");
    return "redirect:/patients/" + patientId;
  }

  @PostMapping("/{patientId}/history")
  public String addHistory(
      @PathVariable UUID patientId,
      @RequestParam HistoryCategory category,
      @RequestParam String description,
      @RequestParam(required = false) String sinceText,
      RedirectAttributes flash) {
    clinical.addHistory(patientId, category, description, sinceText);
    flash.addFlashAttribute("successKey", "patient.history.added");
    return "redirect:/patients/" + patientId;
  }

  @PostMapping("/{patientId}/history/{historyId}/confirm")
  public String confirmHistory(
      @PathVariable UUID patientId, @PathVariable UUID historyId, RedirectAttributes flash) {
    clinical.confirmHistory(patientId, historyId);
    flash.addFlashAttribute("successKey", "patient.history.confirmed");
    return "redirect:/patients/" + patientId;
  }

  @PostMapping("/{patientId}/documents")
  public String uploadDocument(
      @PathVariable UUID patientId,
      @RequestParam DocumentType documentType,
      @RequestParam("file") MultipartFile file,
      RedirectAttributes flash) {
    documents.upload(patientId, documentType, file);
    flash.addFlashAttribute("successKey", "patient.document.uploaded");
    return "redirect:/patients/" + patientId;
  }

  @GetMapping("/{patientId}/documents/{documentId}")
  public ResponseEntity<byte[]> downloadDocument(
      @PathVariable UUID patientId, @PathVariable UUID documentId) throws Exception {
    var meta =
        documents.list(patientId).stream()
            .filter(d -> d.getId().equals(documentId))
            .findFirst()
            .orElseThrow();
    try (var in =
        documents
            .open(patientId, documentId)
            .orElseThrow(() -> new IllegalStateException("missing object"))) {
      byte[] bytes = in.readAllBytes();
      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              "inline; filename=\""
                  + (meta.getOriginalFilename() != null ? meta.getOriginalFilename() : "document")
                  + "\"")
          .contentType(MediaType.parseMediaType(meta.getContentType()))
          .body(bytes);
    }
  }
}
