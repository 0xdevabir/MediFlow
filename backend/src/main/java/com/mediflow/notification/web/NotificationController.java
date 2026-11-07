package com.mediflow.notification.web;

import com.mediflow.notification.domain.NotificationPreference;
import com.mediflow.notification.service.NotificationInbox;
import com.mediflow.notification.service.PreferenceService;
import com.mediflow.shared.web.CurrentUser;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** In-app inbox and notification preferences for any signed-in user. */
@Controller
@RequestMapping("/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

  private final NotificationInbox inbox;
  private final PreferenceService preferences;
  private final CurrentUser currentUser;

  public NotificationController(
      NotificationInbox inbox, PreferenceService preferences, CurrentUser currentUser) {
    this.inbox = inbox;
    this.preferences = preferences;
    this.currentUser = currentUser;
  }

  @GetMapping
  public String inbox(Model model) {
    model.addAttribute("rows", inbox.inbox(currentUser.requireUserId()));
    return "notification/inbox";
  }

  @PostMapping("/{id}/read")
  public String markRead(@PathVariable UUID id, RedirectAttributes ra) {
    if (!inbox.markRead(currentUser.requireUserId(), id)) {
      ra.addFlashAttribute("errorKey", "notification.error.notFound");
    }
    return "redirect:/notifications";
  }

  @PostMapping("/read-all")
  public String markAllRead() {
    inbox.markAllRead(currentUser.requireUserId());
    return "redirect:/notifications";
  }

  @GetMapping("/preferences")
  public String preferences(Model model) {
    NotificationPreference p = preferences.get(currentUser.requireUserId());
    model.addAttribute("pref", p);
    return "notification/preferences";
  }

  @PostMapping("/preferences")
  public String savePreferences(
      @RequestParam(defaultValue = "false") boolean sms,
      @RequestParam(defaultValue = "false") boolean email,
      @RequestParam(defaultValue = "false") boolean push,
      @RequestParam(defaultValue = "false") boolean inApp,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime quietStart,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime quietEnd,
      @RequestParam(defaultValue = "en") String locale,
      RedirectAttributes ra) {
    preferences.save(currentUser.requireUserId(), sms, email, push, inApp, quietStart, quietEnd, locale);
    ra.addFlashAttribute("successKey", "notification.prefs.saved");
    return "redirect:/notifications/preferences";
  }
}
