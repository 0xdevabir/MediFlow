package com.mediflow.directory.web;

import com.mediflow.directory.service.DirectoryValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@ControllerAdvice(basePackages = "com.mediflow.directory.web")
public class DirectoryExceptionAdvice {

  @ExceptionHandler(DirectoryValidationException.class)
  public RedirectView validation(
      DirectoryValidationException ex, HttpServletRequest request, RedirectAttributes flash) {
    flash.addFlashAttribute("errorKey", ex.getMessageKey());
    String referer = request.getHeader("Referer");
    String target =
        referer != null && referer.contains("/directory/")
            ? referer
            : "/directory/admin";
    // Strip scheme/host if absolute referer so RedirectView stays relative.
    int pathIdx = target.indexOf("/directory/");
    if (pathIdx >= 0) {
      target = target.substring(pathIdx);
      int q = target.indexOf('?');
      if (q >= 0) {
        target = target.substring(0, q);
      }
    }
    return new RedirectView(target, true);
  }
}
