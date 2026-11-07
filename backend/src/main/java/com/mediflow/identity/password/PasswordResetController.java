package com.mediflow.identity.password;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Public (unauthenticated) forgot / reset password pages. */
@Controller
@RequestMapping("/identity/password")
public class PasswordResetController {

  private final PasswordResetService resets;

  public PasswordResetController(PasswordResetService resets) {
    this.resets = resets;
  }

  @GetMapping("/forgot")
  public String forgotForm() {
    return "identity/password-forgot";
  }

  @PostMapping("/forgot")
  public String forgot(@RequestParam("username") String username, RedirectAttributes flash) {
    String base =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/identity/password/reset")
            .toUriString();
    resets.requestReset(username, base);
    // Same answer whether or not the account exists.
    flash.addFlashAttribute("successKey", "identity.password.forgot.sent");
    return "redirect:/identity/password/forgot";
  }

  @GetMapping("/reset")
  public String resetForm(@RequestParam(name = "token", required = false) String token, Model model) {
    model.addAttribute("token", token);
    model.addAttribute("tokenValid", resets.isUsable(token));
    model.addAttribute("violations", List.of());
    return "identity/password-reset";
  }

  @PostMapping("/reset")
  public String reset(
      @RequestParam("token") String token,
      @RequestParam("password") String password,
      @RequestParam("confirm") String confirm,
      Model model,
      RedirectAttributes flash) {
    if (!password.equals(confirm)) {
      model.addAttribute("token", token);
      model.addAttribute("tokenValid", resets.isUsable(token));
      model.addAttribute("violations", List.of("identity.password.reset.mismatch"));
      return "identity/password-reset";
    }
    PasswordResetService.ResetResult result = resets.reset(token, password);
    if (result.ok()) {
      return "redirect:/login?reset";
    }
    model.addAttribute("token", token);
    model.addAttribute("tokenValid", result.tokenValid());
    model.addAttribute("violations", result.violations());
    return "identity/password-reset";
  }
}
