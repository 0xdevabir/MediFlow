package com.mediflow.directory.web;

import com.mediflow.directory.repo.DirectoryJdbc;
import com.mediflow.directory.service.DirectoryAdminService;
import com.mediflow.directory.service.SlotGenerationService;
import com.mediflow.shared.domain.LabTest;
import java.math.BigDecimal;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/directory/admin")
@PreAuthorize("hasRole('ADMIN')")
public class DirectoryAdminController {

  private final DirectoryAdminService admin;
  private final DirectoryJdbc jdbc;

  public DirectoryAdminController(DirectoryAdminService admin, DirectoryJdbc jdbc) {
    this.admin = admin;
    this.jdbc = jdbc;
  }

  private static boolean flag(Boolean value) {
    return Boolean.TRUE.equals(value);
  }

  @GetMapping
  public String index(Model model) {
    model.addAttribute("pageTitleKey", "directory.admin.title");
    model.addAttribute("hospitals", admin.listHospitals());
    model.addAttribute("departments", admin.listDepartments());
    model.addAttribute("practitioners", admin.listPractitioners());
    model.addAttribute("roles", admin.listRoles());
    model.addAttribute("tests", admin.listTests());
    model.addAttribute("packages", admin.listPackages());
    return "directory/admin/index";
  }

  // ---- hospitals -----------------------------------------------------------

  @GetMapping("/hospitals/new")
  public String newHospital(Model model) {
    return hospitalForm(null, model);
  }

  @GetMapping("/hospitals/{id}")
  public String editHospital(@PathVariable UUID id, Model model) {
    return hospitalForm(id, model);
  }

  private String hospitalForm(UUID id, Model model) {
    model.addAttribute("pageTitleKey", "directory.admin.hospitals");
    model.addAttribute("allHospitals", admin.listHospitals());
    model.addAttribute(
        "hospital", id == null ? null : jdbc.hospital(id).orElseThrow());
    return "directory/admin/hospital-form";
  }

  @PostMapping("/hospitals")
  public String saveHospital(
      @RequestParam(required = false) UUID id,
      @RequestParam String name,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String address,
      @RequestParam(required = false) String phone,
      @RequestParam(required = false) String emergencyPhone,
      @RequestParam(required = false) String ambulancePhone,
      @RequestParam(required = false) String openingHours,
      @RequestParam(required = false) UUID parentId,
      @RequestParam(required = false) Boolean active,
      RedirectAttributes flash) {
    admin.saveHospital(
        id, name, type, address, phone, emergencyPhone, ambulancePhone, openingHours, parentId, flag(active));
    flash.addFlashAttribute("successKey", "directory.admin.saved");
    return "redirect:/directory/admin";
  }

  // ---- departments ---------------------------------------------------------

  @GetMapping("/departments/new")
  public String newDepartment(Model model) {
    return departmentForm(null, model);
  }

  @GetMapping("/departments/{id}")
  public String editDepartment(@PathVariable UUID id, Model model) {
    return departmentForm(id, model);
  }

  private String departmentForm(UUID id, Model model) {
    model.addAttribute("pageTitleKey", "directory.admin.departments");
    model.addAttribute("allHospitals", admin.listHospitals());
    model.addAttribute("department", id == null ? null : jdbc.department(id).orElseThrow());
    return "directory/admin/department-form";
  }

  @PostMapping("/departments")
  public String saveDepartment(
      @RequestParam(required = false) UUID id,
      @RequestParam UUID hospitalId,
      @RequestParam String name,
      @RequestParam(required = false) Boolean clinical,
      RedirectAttributes flash) {
    admin.saveDepartment(id, hospitalId, name, flag(clinical));
    flash.addFlashAttribute("successKey", "directory.admin.saved");
    return "redirect:/directory/admin";
  }

  // ---- practitioners -------------------------------------------------------

  @GetMapping("/practitioners/new")
  public String newPractitioner(Model model) {
    return practitionerForm(null, model);
  }

  @GetMapping("/practitioners/{id}")
  public String editPractitioner(@PathVariable UUID id, Model model) {
    return practitionerForm(id, model);
  }

  private String practitionerForm(UUID id, Model model) {
    model.addAttribute("pageTitleKey", "directory.admin.practitioners");
    model.addAttribute(
        "practitioner", id == null ? null : jdbc.practitioner(id).orElseThrow());
    return "directory/admin/practitioner-form";
  }

  @PostMapping("/practitioners")
  public String savePractitioner(
      @RequestParam(required = false) UUID id,
      @RequestParam String name,
      @RequestParam(required = false) String roleLabel,
      @RequestParam(required = false) String licenceNo,
      @RequestParam(required = false) String specialty,
      @RequestParam(required = false) UUID userId,
      RedirectAttributes flash) {
    admin.savePractitioner(id, name, roleLabel, licenceNo, specialty, userId);
    flash.addFlashAttribute("successKey", "directory.admin.saved");
    return "redirect:/directory/admin";
  }

  // ---- roles ---------------------------------------------------------------

  @GetMapping("/roles/new")
  public String newRole(Model model) {
    return roleForm(null, model);
  }

  @GetMapping("/roles/{id}")
  public String editRole(@PathVariable UUID id, Model model) {
    return roleForm(id, model);
  }

  private String roleForm(UUID id, Model model) {
    model.addAttribute("pageTitleKey", "directory.admin.roles");
    model.addAttribute("practitioners", admin.listPractitioners());
    model.addAttribute("hospitals", admin.listHospitals());
    model.addAttribute("departments", admin.listDepartments());
    model.addAttribute("role", id == null ? null : jdbc.doctorRole(id).orElseThrow());
    return "directory/admin/role-form";
  }

  @PostMapping("/roles")
  public String saveRole(
      @RequestParam(required = false) UUID id,
      @RequestParam UUID practitionerId,
      @RequestParam UUID hospitalId,
      @RequestParam UUID departmentId,
      @RequestParam(required = false) String schedule,
      @RequestParam(required = false) Boolean active,
      RedirectAttributes flash) {
    admin.saveRole(id, practitionerId, hospitalId, departmentId, schedule, flag(active));
    flash.addFlashAttribute("successKey", "directory.admin.saved");
    return "redirect:/directory/admin";
  }

  @PostMapping("/roles/{id}/reconcile")
  public String reconcile(@PathVariable UUID id, RedirectAttributes flash) {
    SlotGenerationService.Result r = admin.reconcileRole(id);
    flash.addFlashAttribute(
        "successMessage", "Slots regenerated (" + r.created() + " created).");
    return "redirect:/directory/admin";
  }

  // ---- tests ---------------------------------------------------------------

  @GetMapping("/tests/new")
  public String newTest(Model model) {
    return testForm(null, model);
  }

  @GetMapping("/tests/{id}")
  public String editTest(@PathVariable UUID id, Model model) {
    return testForm(id, model);
  }

  private String testForm(UUID id, Model model) {
    model.addAttribute("pageTitleKey", "directory.admin.tests");
    LabTest test =
        id == null
            ? null
            : admin.listTests().stream().filter(t -> t.getId().equals(id)).findFirst().orElseThrow();
    model.addAttribute("test", test);
    return "directory/admin/test-form";
  }

  @PostMapping("/tests")
  public String saveTest(
      @RequestParam(required = false) UUID id,
      @RequestParam String code,
      @RequestParam String name,
      @RequestParam(required = false) String loincCode,
      @RequestParam(required = false) Boolean directAllowed,
      @RequestParam(required = false) BigDecimal price,
      @RequestParam(required = false) Boolean active,
      RedirectAttributes flash) {
    admin.saveTest(id, code, name, loincCode, flag(directAllowed), price, flag(active));
    flash.addFlashAttribute("successKey", "directory.admin.saved");
    return "redirect:/directory/admin";
  }

  // ---- packages ------------------------------------------------------------

  @GetMapping("/packages/new")
  public String newPackage(Model model) {
    return packageForm(null, model);
  }

  @GetMapping("/packages/{id}")
  public String editPackage(@PathVariable UUID id, Model model) {
    return packageForm(id, model);
  }

  private String packageForm(UUID id, Model model) {
    model.addAttribute("pageTitleKey", "directory.admin.packages");
    model.addAttribute("hospitals", admin.listHospitals());
    model.addAttribute(
        "directTests",
        admin.listTests().stream().filter(LabTest::getDirectAllowed).toList());
    model.addAttribute(
        "pkg",
        id == null
            ? null
            : admin.listPackages().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow());
    model.addAttribute("selectedTestIds", id == null ? List.of() : admin.packageTestIds(id));
    return "directory/admin/package-form";
  }

  @PostMapping("/packages")
  public String savePackage(
      @RequestParam(required = false) UUID id,
      @RequestParam String name,
      @RequestParam(required = false) UUID hospitalId,
      @RequestParam(required = false) BigDecimal price,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) List<UUID> testIds,
      RedirectAttributes flash) {
    admin.savePackage(id, name, hospitalId, price, flag(active), testIds);
    flash.addFlashAttribute("successKey", "directory.admin.saved");
    return "redirect:/directory/admin";
  }
}
