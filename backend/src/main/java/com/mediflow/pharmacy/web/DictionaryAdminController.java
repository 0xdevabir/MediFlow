package com.mediflow.pharmacy.web;

import com.mediflow.pharmacy.dictionary.DgdaImportService;
import com.mediflow.pharmacy.dictionary.DgdaImportService.ImportResult;
import com.mediflow.pharmacy.dictionary.DrugDictionaryPort;
import com.mediflow.pharmacy.dictionary.DrugMatch;
import com.mediflow.shared.web.CurrentUser;
import java.io.IOException;
import java.util.List;
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
@RequestMapping("/pharmacy/dictionary")
@PreAuthorize("hasRole('ADMIN')")
public class DictionaryAdminController {

  private final DgdaImportService imports;
  private final DrugDictionaryPort dictionary;
  private final CurrentUser currentUser;

  public DictionaryAdminController(
      DgdaImportService imports, DrugDictionaryPort dictionary, CurrentUser currentUser) {
    this.imports = imports;
    this.dictionary = dictionary;
    this.currentUser = currentUser;
  }

  @GetMapping
  public String page(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) UUID importId,
      Model model) {
    model.addAttribute("pageTitleKey", "pharmacy.dictionary.title");
    model.addAttribute("imports", imports.listImports());
    if (q != null && !q.isBlank()) {
      List<DrugMatch> suggestions = dictionary.suggest(q, 20);
      model.addAttribute("query", q);
      model.addAttribute("suggestions", suggestions);
      model.addAttribute("exact", dictionary.resolveGeneric(q).orElse(null));
      model.addAttribute("brands", dictionary.findBrandProducts(q));
    }
    if (importId != null) {
      model.addAttribute("importId", importId);
      model.addAttribute("unmappedReport", imports.unmappedReport(importId));
    }
    return "pharmacy/dictionary";
  }

  @PostMapping("/import")
  public String importCsv(
      @RequestParam("file") MultipartFile file,
      @RequestParam(defaultValue = "DGDA register") String sourceLabel,
      RedirectAttributes ra)
      throws IOException {
    if (file == null || file.isEmpty()) {
      ra.addFlashAttribute("errorKey", "pharmacy.dictionary.emptyFile");
      return "redirect:/pharmacy/dictionary";
    }
    try {
      ImportResult result =
          imports.importCsv(
              sourceLabel, file.getOriginalFilename(), file.getBytes(), currentUser.requireUserId());
      ra.addFlashAttribute(
          "successMessage",
          "Import v"
              + result.importVersion()
              + ": "
              + result.createdBrands()
              + " brands created, "
              + result.updatedBrands()
              + " updated, "
              + result.unmappedRows()
              + " unmapped");
      return "redirect:/pharmacy/dictionary?importId=" + result.importId();
    } catch (IllegalArgumentException e) {
      ra.addFlashAttribute("errorMessage", e.getMessage());
      return "redirect:/pharmacy/dictionary";
    }
  }

  @GetMapping("/imports/{id}/unmapped")
  public String unmapped(@PathVariable UUID id, Model model) {
    model.addAttribute("pageTitleKey", "pharmacy.dictionary.unmapped");
    model.addAttribute("importId", id);
    model.addAttribute("unmappedReport", imports.unmappedReport(id));
    return "pharmacy/dictionary-unmapped";
  }
}
