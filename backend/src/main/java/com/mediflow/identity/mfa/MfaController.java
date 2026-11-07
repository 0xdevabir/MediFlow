package com.mediflow.identity.mfa;

import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.port.ClockPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** TOTP enrolment (QR), login verification and 5-minute step-up pages. */
@Controller
@RequestMapping("/identity/mfa")
public class MfaController {

  private final MfaService mfa;
  private final TotpService totp;
  private final ClockPort clock;

  public MfaController(MfaService mfa, TotpService totp, ClockPort clock) {
    this.mfa = mfa;
    this.totp = totp;
    this.clock = clock;
  }

  @GetMapping("/enrol")
  public String enrolForm(Authentication auth, Model model) {
    AppUser user = requireUser(auth);
    if (mfa.hasVerifiedSecret(user.getId())) {
      return "redirect:/identity/mfa/verify";
    }
    String secret = mfa.pendingEnrolmentSecret(user.getId());
    model.addAttribute("qr", totp.qrDataUri(user.getUsername(), secret));
    model.addAttribute("secret", secret);
    return "identity/mfa-enrol";
  }

  @PostMapping("/enrol")
  public String enrol(
      @RequestParam("code") String code,
      Authentication auth,
      HttpServletRequest request,
      RedirectAttributes flash) {
    AppUser user = requireUser(auth);
    return finish(mfa.confirmEnrolment(user.getId(), code), user, "/", "/identity/mfa/enrol", request, flash);
  }

  @GetMapping("/verify")
  public String verifyForm(Authentication auth, Model model) {
    AppUser user = requireUser(auth);
    if (!mfa.hasVerifiedSecret(user.getId())) {
      return "redirect:/identity/mfa/enrol";
    }
    model.addAttribute("stepUp", false);
    model.addAttribute("continueUrl", "/");
    return "identity/mfa-verify";
  }

  @PostMapping("/verify")
  public String verify(
      @RequestParam("code") String code,
      Authentication auth,
      HttpServletRequest request,
      RedirectAttributes flash) {
    AppUser user = requireUser(auth);
    return finish(mfa.verify(user.getId(), code), user, "/", "/identity/mfa/verify", request, flash);
  }

  @GetMapping("/step-up")
  public String stepUpForm(
      @RequestParam(name = "continue", required = false) String continueUrl,
      Authentication auth,
      Model model) {
    AppUser user = requireUser(auth);
    if (!mfa.hasVerifiedSecret(user.getId())) {
      return "redirect:/identity/mfa/enrol";
    }
    model.addAttribute("stepUp", true);
    model.addAttribute("continueUrl", SafeRedirect.localOrRoot(continueUrl));
    return "identity/mfa-verify";
  }

  @PostMapping("/step-up")
  public String stepUp(
      @RequestParam("code") String code,
      @RequestParam(name = "continue", required = false) String continueUrl,
      Authentication auth,
      HttpServletRequest request,
      RedirectAttributes flash) {
    AppUser user = requireUser(auth);
    String target = SafeRedirect.localOrRoot(continueUrl);
    String retry =
        "/identity/mfa/step-up?continue="
            + java.net.URLEncoder.encode(target, java.nio.charset.StandardCharsets.UTF_8);
    return finish(mfa.verify(user.getId(), code), user, target, retry, request, flash);
  }

  private String finish(
      MfaService.VerifyResult result,
      AppUser user,
      String success,
      String retry,
      HttpServletRequest request,
      RedirectAttributes flash) {
    switch (result) {
      case OK -> {
        request.changeSessionId();
        MfaSession.markVerified(request.getSession(), user.getUsername(), clock.now());
        return "redirect:" + success;
      }
      case LOCKED -> {
        HttpSession session = request.getSession(false);
        if (session != null) {
          session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return "redirect:/login?error";
      }
      default -> {
        flash.addFlashAttribute("errorKey", "identity.mfa.invalid");
        return "redirect:" + retry;
      }
    }
  }

  private AppUser requireUser(Authentication auth) {
    if (auth == null) {
      throw new org.springframework.security.access.AccessDeniedException("not signed in");
    }
    return mfa.user(auth.getName())
        .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("unknown user"));
  }
}
