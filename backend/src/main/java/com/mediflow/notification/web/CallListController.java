package com.mediflow.notification.web;

import com.mediflow.notification.service.StaffCallListService;
import com.mediflow.shared.web.CurrentUser;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Risk R7: staff phone-call list of notifications that could not be delivered. */
@Controller
@RequestMapping("/notifications/call-list")
@PreAuthorize(StaffCallListService.STAFF_ROLES)
public class CallListController {

  private final StaffCallListService callList;
  private final CurrentUser currentUser;

  public CallListController(StaffCallListService callList, CurrentUser currentUser) {
    this.callList = callList;
    this.currentUser = currentUser;
  }

  @GetMapping
  public String list(Model model) {
    model.addAttribute("rows", callList.openEntries(currentUser.requireUserId()));
    return "notification/call-list";
  }

  @PostMapping("/{id}/resolve")
  public String resolve(@PathVariable UUID id, RedirectAttributes ra) {
    try {
      callList.resolve(id, currentUser.requireUserId());
      ra.addFlashAttribute("successKey", "notification.callList.resolved");
    } catch (IllegalArgumentException e) {
      ra.addFlashAttribute("errorKey", "notification.error.notFound");
    }
    return "redirect:/notifications/call-list";
  }
}
