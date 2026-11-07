PatientProfileController.java 244L cognitive
// /Users/mdabirhossain/Documents/WebDevelopment/Shared-Startups/MediFlow-wt/T02-patient/backend/src/main/java/com/mediflow/patient/web/PatientProfileController.java
§ block block (L1-L29)
package com.mediflow.patient.web;

import com.mediflow.patient.domain.ContactChannel;
import com.mediflow.patient.domain.DocumentType;
import com.mediflow.patient.domain.HistoryCategory;
import com.mediflow.patient.service.ContactVerificationService;
import com.mediflow.patient.service.OtpIssuer;
import com.mediflow.patient.service.PatientAccessGuard;
import com.mediflow.patient.service.PatientClinicalHistoryService;
import com.mediflow.patient.service.PatientDocumentService;
import com.mediflow.patient.service.PatientProfileService;
import com.mediflow.patient.service.PatientView;
import com.mediflow.patient.service.QrTokenService;
import java.time.LocalDate;
import java.util.UUID;
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

§ type PatientProfileController (L30-L40)
@Controller
@RequestMapping("/patients")
public class PatientProfileController {

  private final PatientProfileService profiles;
  private final PatientClinicalHistoryService clinical;
  private final PatientDocumentService documents;
  private final ContactVerificationService contacts;
  private final PatientAccessGuard access;
  private final QrTokenService qrTokens;

§ function function (L41-L54)
  public PatientProfileController(
      PatientProfileService profiles,
      PatientClinicalHistoryService clinical,
      PatientDocumentService documents,
      ContactVerificationService contacts,
      PatientAccessGuard access,
      QrTokenService qrTokens) {
    this.profiles = profiles;
    this.clinical = clinical;
    this.documents = documents;
    this.contacts = contacts;
    this.access = access;
    this.qrTokens = qrTokens;
  }
// ... 1 lines omitted
§ function function (L56-L64)
  @GetMapping("/me")
  public String me() {
    UUID id =
        access
            .ownPatientId()
            .orElseThrow(
                () -> new org.springframework.security.access.AccessDeniedException("not a patient"));
    return "redirect:/patients/" + id;
  }
// ... 1 lines omitted
§ function function (L66-L72)
  @GetMapping("/search")
  public String search(@RequestParam(value = "q", required = false) String q, Model model) {
    model.addAttribute("pageTitleKey", "patient.search.title");
    model.addAttribute("q", q);
    model.addAttribute("results", profiles.search(q));
    return "patient/search";
  }
// ... 1 lines omitted
§ function function (L74-L87)
  @GetMapping("/{patientId}")
  public String profile(@PathVariable UUID patientId, Model model) {
    PatientView view = profiles.get(patientId);
    model.addAttribute("pageTitleKey", "patient.profile.title");
    model.addAttribute("patient", view);
    model.addAttribute(
        "allergies",
        access.canReadClinicalHistory(patientId)
            ? clinical.listAllergies(patientId)
            : java.util.List.of());
    model.addAttribute(
        "history",
        access.canReadClinicalHistory(patientId)
            ? clinical.listHistory(patientId)
            : java.util.List.of());
    model.addAttribute(
        "documents",
        access.canAccessDocuments(patientId) ? documents.list(patientId) : java.util.List.of());
    model.addAttribute("canUpdateContact", access.canUpdateContact(patientId));
    model.addAttribute("canConfirmClinical", access.canConfirmClinicalHistory(patientId));
    model.addAttribute("canAccessDocuments", access.canAccessDocuments(patientId));
    model.addAttribute("isSelf", access.isSelf(patientId));
    QrTokenService.IssuedToken token = profiles.issueCardToken(patientId);
    model.addAttribute("qrDataUri", qrTokens.qrDataUri(token.token()));
    model.addAttribute("qrExpiresAt", token.expiresAt());
    return "patient/profile";
  }
// ... 1 lines omitted
§ function function (L89-L98)
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
// ... 1 lines omitted
§ function function (L100-L110)
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
// ... 1 lines omitted
§ function function (L112-L118)
  @PostMapping("/{patientId}/otp/send")
  public String sendOtp(
      @PathVariable UUID patientId, @RequestParam ContactChannel channel, RedirectAttributes flash) {
    contacts.resend(patientId, channel);
    flash.addFlashAttribute("successKey", "patient.otp.sent");
    return "redirect:/patients/" + patientId;
  }
9/20 chunks shown (995 tokens)
[lean-ctx] full source: read "/Users/mdabirhossain/Documents/WebDevelopment/Shared-Startups/MediFlow-wt/T02-patient/backend/src/main/java/com/mediflow/patient/web/PatientProfileController.java" directly (no MCP)  ·  or ctx_read("/Users/mdabirhossain/Documents/WebDevelopment/Shared-Startups/MediFlow-wt/T02-patient/backend/src/main/java/com/mediflow/patient/web/PatientProfileController.java", mode="full")
