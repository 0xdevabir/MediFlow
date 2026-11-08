package com.mediflow.identity.staff;

import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Role;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** ADMIN staff management UI on top of {@link StaffUserService}. */
@Controller
@RequestMapping("/identity/staff")
@PreAuthorize("hasRole('ADMIN')")
public class StaffController {

  private final StaffUserService staff;

  public StaffController(StaffUserService staff) {
    this.staff = staff;
  }

  @GetMapping
  public String list(Model model) {
    model.addAttribute("rows", staff.listStaffRows());
    model.addAttribute("staffRoles", StaffUserService.STAFF_ROLES);
    return "identity/staff-list";
  }

  @GetMapping("/new")
  public String createForm(Model model) {
    model.addAttribute("staffRoles", StaffUserService.STAFF_ROLES);
    model.addAttribute("mode", "create");
    return "identity/staff-form";
  }

  @PostMapping
  public String create(
      @RequestParam("username") String username,
      @RequestParam("displayName") String displayName,
      @RequestParam("initialPassword") String initialPassword,
      @RequestParam(name = "roles", required = false) List<String> roles,
      @RequestParam(name = "email", required = false) String email,
      @RequestParam(name = "phone", required = false) String phone,
      @RequestParam(name = "locale", required = false) String locale,
      RedirectAttributes flash) {
    try {
      AppUser created =
          staff.createUser(
              new StaffUserService.NewStaffUser(
                  username, displayName, initialPassword, parseRoles(roles), email, phone, locale));
      flash.addFlashAttribute("successKey", "identity.staff.created");
      return "redirect:/identity/staff/" + created.getId();
    } catch (StaffUserService.StaffValidationException e) {
      flashFirstError(flash, e);
      return "redirect:/identity/staff/new";
    }
  }

  @GetMapping("/{id}")
  public String edit(@PathVariable("id") UUID id, Model model, RedirectAttributes flash) {
    try {
      AppUser user = staff.getStaff(id);
      model.addAttribute("user", user);
      model.addAttribute("locked", staff.isLocked(id));
      model.addAttribute("practitioner", staff.practitionerForUser(id).orElse(null));
      model.addAttribute("staffRoles", StaffUserService.STAFF_ROLES);
      model.addAttribute("mode", "edit");
      return "identity/staff-form";
    } catch (StaffUserService.StaffValidationException e) {
      flashFirstError(flash, e);
      return "redirect:/identity/staff";
    }
  }

  @PostMapping("/{id}/roles")
  public String assignRoles(
      @PathVariable("id") UUID id,
      @RequestParam(name = "roles", required = false) List<String> roles,
      RedirectAttributes flash) {
    return mutate(id, flash, () -> staff.assignRoles(id, parseRoles(roles)), "identity.staff.roles.saved");
  }

  @PostMapping("/{id}/enable")
  public String enable(@PathVariable("id") UUID id, RedirectAttributes flash) {
    return mutate(id, flash, () -> staff.setEnabled(id, true), "identity.staff.enabled");
  }

  @PostMapping("/{id}/disable")
  public String disable(@PathVariable("id") UUID id, RedirectAttributes flash) {
    return mutate(id, flash, () -> staff.setEnabled(id, false), "identity.staff.disabled");
  }

  @PostMapping("/{id}/unlock")
  public String unlock(@PathVariable("id") UUID id, RedirectAttributes flash) {
    return mutate(id, flash, () -> staff.unlock(id), "identity.staff.unlocked");
  }

  @PostMapping("/{id}/reset-mfa")
  public String resetMfa(@PathVariable("id") UUID id, RedirectAttributes flash) {
    return mutate(id, flash, () -> staff.resetMfa(id), "identity.staff.mfaReset");
  }

  @PostMapping("/{id}/practitioner/link")
  public String linkPractitioner(
      @PathVariable("id") UUID id,
      @RequestParam("practitionerId") UUID practitionerId,
      RedirectAttributes flash) {
    return mutate(
        id, flash, () -> staff.linkPractitioner(id, practitionerId), "identity.staff.practitioner.linkedOk");
  }

  @PostMapping("/{id}/practitioner")
  public String createPractitioner(
      @PathVariable("id") UUID id,
      @RequestParam(name = "name", required = false) String name,
      @RequestParam(name = "roleLabel", required = false) String roleLabel,
      @RequestParam(name = "licenceNo", required = false) String licenceNo,
      RedirectAttributes flash) {
    return mutate(
        id,
        flash,
        () -> staff.createPractitioner(id, name, roleLabel, licenceNo),
        "identity.staff.practitioner.created");
  }

  private String mutate(UUID id, RedirectAttributes flash, Runnable action, String successKey) {
    try {
      action.run();
      flash.addFlashAttribute("successKey", successKey);
    } catch (StaffUserService.StaffValidationException e) {
      flashFirstError(flash, e);
    } catch (IllegalArgumentException | IllegalStateException e) {
      flash.addFlashAttribute("errorKey", e.getMessage());
    }
    return "redirect:/identity/staff/" + id;
  }

  private static Set<Role> parseRoles(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return Set.of();
    }
    Set<Role> roles = EnumSet.noneOf(Role.class);
    for (String r : raw) {
      if (r == null || r.isBlank()) {
        continue;
      }
      try {
        roles.add(Role.valueOf(r.strip()));
      } catch (IllegalArgumentException ignored) {
        // ignore unknown role names from the form
      }
    }
    return roles;
  }
}
