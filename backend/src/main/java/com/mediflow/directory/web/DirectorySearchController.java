package com.mediflow.directory.web;

import com.mediflow.directory.api.DirectoryViews.SearchCriteria;
import com.mediflow.directory.api.DirectoryViews.SearchResult;
import com.mediflow.directory.service.DirectoryQueryService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Public hospital / doctor directory search (no login). */
@Controller
@RequestMapping("/directory")
public class DirectorySearchController {

  private final DirectoryQueryService queries;

  public DirectorySearchController(DirectoryQueryService queries) {
    this.queries = queries;
  }

  @GetMapping({"", "/", "/search"})
  public String search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) UUID hospitalId,
      @RequestParam(required = false) String department,
      @RequestParam(required = false) String specialty,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Model model) {
    SearchCriteria criteria = new SearchCriteria(q, hospitalId, department, specialty, date);
    SearchResult result = queries.search(criteria);
    model.addAttribute("pageTitleKey", "directory.search.title");
    model.addAttribute("criteria", criteria);
    model.addAttribute("result", result);
    model.addAttribute("hospitals", queries.hospitalOptions());
    model.addAttribute("departments", queries.clinicalDepartmentNames());
    model.addAttribute("specialties", queries.specialtyNames());
    model.addAttribute("directTests", queries.directTests());
    model.addAttribute("packages", queries.packages(hospitalId));
    return "directory/search";
  }

  @GetMapping("/tests")
  public String tests(@RequestParam(required = false) UUID hospitalId, Model model) {
    model.addAttribute("pageTitleKey", "directory.tests.title");
    model.addAttribute("directTests", queries.directTests());
    model.addAttribute("packages", queries.packages(hospitalId));
    model.addAttribute("hospitals", queries.hospitalOptions());
    model.addAttribute("hospitalId", hospitalId);
    return "directory/tests";
  }

  @GetMapping("/packages")
  public String packages(@RequestParam(required = false) UUID hospitalId, Model model) {
    return tests(hospitalId, model);
  }
}
