package com.mediflow.identity.consent;

import com.mediflow.identity.access.AccessRules;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Share with another doctor". Patients open {@code /identity/sharing} for their own record; an
 * assigned doctor opens {@code /identity/sharing?patientId=..}.
 */
@Controller
@RequestMapping("/identity/sharing")
@PreAuthorize("hasAnyRole('PATIENT','DOCTOR','ER','ADMIN')")
public class SharingController {

  /** View row. */
  public record ShareView(
      UUID id, UUID granteeUserId, String reason, LocalDateTime startsAt, LocalDateTime expiresAt,
      boolean active, boolean revoked) {}

  private final ReferralShareService service;
  private final com.mediflow.shared.port.ClockPort clock;
  private final com.mediflow.identity.breakglass.IdentityAdminDirectory directory;

  public SharingController(
      ReferralShareService service,
      com.mediflow.shared.port.ClockPort clock,
      com.mediflow.identity.breakglass.IdentityAdminDirectory directory) {
    this.service = service;
    this.clock = clock;
    this.directory = directory;
  }

  @GetMapping
  public String page(@RequestParam(name = "patientId", required = false) UUID patientId, Model model) {
    UUID pid = resolve(patientId);
    Instant now = clock.now();
    var rows = service.listForPatient(pid);
    var names = directory.usernames(rows.stream().map(ReferralShare::getGranteeUserId).distinct().toList());
    model.addAttribute("patientId", pid);
    model.addAttribute("byPatient", service.ownPatientId().map(pid::equals).orElse(false));
    model.addAttribute("names", names);
    model.addAttribute(
        "shares",
        rows.stream()
            .map(
                s ->
                    new ShareView(
                        s.getId(),
                        s.getGranteeUserId(),
                        s.getReason(),
                        LocalDateTime.ofInstant(s.getStartsAt(), AccessRules.CLINIC_ZONE),
                        LocalDateTime.ofInstant(s.getExpiresAt(), AccessRules.CLINIC_ZONE),
                        s.activeAt(now),
                        s.getRevokedAt() != null))
            .toList());
    model.addAttribute("maxDays", ReferralShareService.MAX_DURATION.toDays());
    return "identity/sharing";
  }

  @PostMapping
  public String share(
      @RequestParam("patientId") UUID patientId,
      @RequestParam("grantee") String grantee,
      @RequestParam(name = "days", defaultValue = "7") int days,
      @RequestParam(name = "reason", required = false) String reason,
      @RequestParam(name = "patientAgreed", defaultValue = "false") boolean patientAgreed,
      RedirectAttributes flash) {
    try {
      service.share(patientId, grantee, Duration.ofDays(days), reason, patientAgreed);
      flash.addFlashAttribute("successKey", "identity.sharing.created");
    } catch (IllegalArgumentException e) {
      flash.addFlashAttribute("errorKey", e.getMessage());
    }
    return "redirect:/identity/sharing?patientId=" + patientId;
  }

  @PostMapping("/{id}/revoke")
  public String revoke(
      @PathVariable("id") UUID id, @RequestParam("patientId") UUID patientId, RedirectAttributes flash) {
    try {
      service.revoke(id);
      flash.addFlashAttribute("successKey", "identity.sharing.revoked");
    } catch (IllegalArgumentException e) {
      flash.addFlashAttribute("errorKey", e.getMessage());
    }
    return "redirect:/identity/sharing?patientId=" + patientId;
  }

  private UUID resolve(UUID patientId) {
    if (patientId != null) {
      return patientId;
    }
    return service.ownPatientId().orElseThrow(() -> new AccessDeniedException("patientId required"));
  }
}
