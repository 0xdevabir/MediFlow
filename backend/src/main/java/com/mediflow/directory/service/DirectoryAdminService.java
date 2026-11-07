package com.mediflow.directory.service;

import com.mediflow.directory.event.PractitionerScheduleChanged;
import com.mediflow.directory.repo.DirectoryJdbc;
import com.mediflow.directory.schedule.WeeklySchedule;
import com.mediflow.shared.domain.Department;
import com.mediflow.shared.domain.Hospital;
import com.mediflow.shared.domain.LabPackage;
import com.mediflow.shared.domain.LabPackageItem;
import com.mediflow.shared.domain.LabTest;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.domain.PractitionerRoleEntity;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.DepartmentRepository;
import com.mediflow.shared.repo.HospitalRepository;
import com.mediflow.shared.repo.LabPackageItemRepository;
import com.mediflow.shared.repo.LabPackageRepository;
import com.mediflow.shared.repo.LabTestRepository;
import com.mediflow.shared.repo.PractitionerRepository;
import com.mediflow.shared.repo.PractitionerRoleEntityRepository;
import com.mediflow.shared.web.CurrentUser;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ADMIN-only directory CRUD. Schedule edits publish {@link PractitionerScheduleChanged}. */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class DirectoryAdminService {

  private final HospitalRepository hospitals;
  private final DepartmentRepository departments;
  private final PractitionerRepository practitioners;
  private final PractitionerRoleEntityRepository roles;
  private final LabTestRepository tests;
  private final LabPackageRepository packages;
  private final LabPackageItemRepository packageItems;
  private final DirectoryJdbc jdbc;
  private final SlotGenerationService slots;
  private final AuditPort audit;
  private final ClockPort clock;
  private final CurrentUser currentUser;
  private final ApplicationEventPublisher events;

  public DirectoryAdminService(
      HospitalRepository hospitals,
      DepartmentRepository departments,
      PractitionerRepository practitioners,
      PractitionerRoleEntityRepository roles,
      LabTestRepository tests,
      LabPackageRepository packages,
      LabPackageItemRepository packageItems,
      DirectoryJdbc jdbc,
      SlotGenerationService slots,
      AuditPort audit,
      ClockPort clock,
      CurrentUser currentUser,
      ApplicationEventPublisher events) {
    this.hospitals = hospitals;
    this.departments = departments;
    this.practitioners = practitioners;
    this.roles = roles;
    this.tests = tests;
    this.packages = packages;
    this.packageItems = packageItems;
    this.jdbc = jdbc;
    this.slots = slots;
    this.audit = audit;
    this.clock = clock;
    this.currentUser = currentUser;
    this.events = events;
  }

  // ---- hospitals -----------------------------------------------------------

  @Transactional(readOnly = true)
  public List<DirectoryJdbc.HospitalRow> listHospitals() {
    return jdbc.allHospitals();
  }

  @Transactional
  public Hospital saveHospital(
      UUID id,
      String name,
      String type,
      String address,
      String phone,
      String emergencyPhone,
      String ambulancePhone,
      String openingHours,
      UUID parentId,
      boolean active) {
    requireName(name);
    if (parentId != null && id != null && jdbc.isSelfOrDescendant(id, parentId)) {
      throw new DirectoryValidationException("directory.error.invalidParent");
    }
    Hospital h = id == null ? new Hospital() : hospitals.findById(id).orElseThrow(this::notFound);
    h.setName(name.trim());
    h.setType(blankToNull(type));
    h.setAddress(blankToNull(address));
    h.setPhone(blankToNull(phone));
    h.setEmergencyPhone(blankToNull(emergencyPhone));
    h.setOpeningHours(blankToNull(openingHours));
    h.setActive(active);
    h = hospitals.save(h);
    jdbc.updateHospitalExtras(h.getId(), parentId, blankToNull(ambulancePhone));
    audit("DIRECTORY_HOSPITAL_SAVE", "hospital", h.getId(), Map.of("name", h.getName()));
    return h;
  }

  // ---- departments ---------------------------------------------------------

  @Transactional(readOnly = true)
  public List<DirectoryJdbc.DepartmentRow> listDepartments() {
    return jdbc.allDepartments();
  }

  @Transactional
  public Department saveDepartment(UUID id, UUID hospitalId, String name, boolean clinical) {
    requireName(name);
    if (hospitalId == null) {
      throw new DirectoryValidationException("directory.error.departmentHospital");
    }
    if (!hospitals.existsById(hospitalId)) {
      throw notFound();
    }
    Department d =
        id == null ? new Department() : departments.findById(id).orElseThrow(this::notFound);
    d.setHospitalId(hospitalId);
    d.setName(name.trim());
    d = departments.save(d);
    jdbc.updateDepartmentClinical(d.getId(), clinical);
    audit("DIRECTORY_DEPARTMENT_SAVE", "department", d.getId(), Map.of("name", d.getName()));
    return d;
  }

  // ---- practitioners -------------------------------------------------------

  @Transactional(readOnly = true)
  public List<DirectoryJdbc.PractitionerRow> listPractitioners() {
    return jdbc.allPractitioners();
  }

  @Transactional
  public Practitioner savePractitioner(
      UUID id, String name, String roleLabel, String licenceNo, String specialty, UUID userId) {
    requireName(name);
    Practitioner p =
        id == null ? new Practitioner() : practitioners.findById(id).orElseThrow(this::notFound);
    p.setName(name.trim());
    p.setRoleLabel(blankToNull(roleLabel));
    p.setLicenceNo(blankToNull(licenceNo));
    p.setUserId(userId);
    p = practitioners.save(p);
    jdbc.updatePractitionerSpecialty(p.getId(), blankToNull(specialty));
    audit("DIRECTORY_PRACTITIONER_SAVE", "practitioner", p.getId(), Map.of("name", p.getName()));
    return p;
  }

  // ---- roles / schedule ----------------------------------------------------

  @Transactional(readOnly = true)
  public List<DirectoryJdbc.DoctorRow> listRoles() {
    return jdbc.allDoctorRoles();
  }

  @Transactional
  public PractitionerRoleEntity saveRole(
      UUID id,
      UUID practitionerId,
      UUID hospitalId,
      UUID departmentId,
      String scheduleJson,
      boolean active) {
    if (practitionerId == null || hospitalId == null || departmentId == null) {
      throw new DirectoryValidationException("directory.error.roleRefs");
    }
    if (!practitioners.existsById(practitionerId)
        || !hospitals.existsById(hospitalId)
        || !departments.existsById(departmentId)) {
      throw notFound();
    }
    WeeklySchedule schedule = WeeklySchedule.parse(scheduleJson);
    String canonical = schedule.isEmpty() ? null : schedule.toJson();

    PractitionerRoleEntity role =
        id == null
            ? new PractitionerRoleEntity()
            : roles.findById(id).orElseThrow(this::notFound);
    String previousSchedule = role.getSchedule();
    boolean previouslyActive = role.getId() != null && role.getActive();
    role.setPractitionerId(practitionerId);
    role.setHospitalId(hospitalId);
    role.setDepartmentId(departmentId);
    role.setSchedule(canonical);
    role.setActive(active);
    role = roles.save(role);

    SlotGenerationService.Result result = slots.reconcile(role);
    boolean scheduleChanged =
        !Objects.equals(previousSchedule, canonical) || previouslyActive != active;
    if (scheduleChanged) {
      events.publishEvent(
          new PractitionerScheduleChanged(
              role.getId(),
              role.getPractitionerId(),
              role.getHospitalId(),
              result.affectedBookedSlotIds(),
              clock.now()));
    }
    audit(
        "DIRECTORY_ROLE_SAVE",
        "practitioner_role",
        role.getId(),
        Map.of(
            "active", active,
            "slotsCreated", result.created(),
            "affectedBooked", result.affectedBookedSlotIds().size()));
    return role;
  }

  @Transactional
  public SlotGenerationService.Result reconcileRole(UUID roleId) {
    SlotGenerationService.Result result = slots.reconcile(roleId);
    audit(
        "DIRECTORY_SLOTS_RECONCILE",
        "practitioner_role",
        roleId,
        Map.of("created", result.created()));
    return result;
  }

  // ---- lab tests -----------------------------------------------------------

  @Transactional(readOnly = true)
  public List<LabTest> listTests() {
    return tests.findAll();
  }

  @Transactional
  public LabTest saveTest(
      UUID id,
      String code,
      String name,
      String loincCode,
      boolean directAllowed,
      BigDecimal price,
      boolean active) {
    requireName(name);
    if (code == null || code.isBlank()) {
      throw new DirectoryValidationException("directory.error.blankCode");
    }
    if (price != null && price.signum() < 0) {
      throw new DirectoryValidationException("directory.error.price");
    }
    LabTest t = id == null ? new LabTest() : tests.findById(id).orElseThrow(this::notFound);
    String trimmedCode = code.trim();
    if (jdbc.labTestCodeTaken(trimmedCode, id)) {
      throw new DirectoryValidationException("directory.error.duplicateCode");
    }
    if (t.getId() != null
        && t.getDirectAllowed()
        && !directAllowed
        && jdbc.packagesContaining(t.getId()) > 0) {
      throw new DirectoryValidationException("directory.error.testInPackage");
    }
    t.setCode(trimmedCode);
    t.setName(name.trim());
    t.setLoincCode(blankToNull(loincCode));
    t.setDirectAllowed(directAllowed);
    t.setPrice(price);
    t.setActive(active);
    t = tests.save(t);
    audit("DIRECTORY_TEST_SAVE", "lab_test", t.getId(), Map.of("code", t.getCode()));
    return t;
  }

  // ---- packages ------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<DirectoryJdbc.PackageRow> listPackages() {
    return jdbc.packages(false, null);
  }

  @Transactional(readOnly = true)
  public List<UUID> packageTestIds(UUID packageId) {
    return jdbc.packageItems(List.of(packageId)).stream()
        .map(i -> i.test().id())
        .toList();
  }

  @Transactional
  public LabPackage savePackage(
      UUID id, String name, UUID hospitalId, BigDecimal price, boolean active, List<UUID> testIds) {
    requireName(name);
    if (price != null && price.signum() < 0) {
      throw new DirectoryValidationException("directory.error.price");
    }
    if (hospitalId != null && !hospitals.existsById(hospitalId)) {
      throw notFound();
    }
    Set<UUID> unique = new HashSet<>(testIds == null ? List.of() : testIds);
    for (UUID testId : unique) {
      LabTest t = tests.findById(testId).orElseThrow(this::notFound);
      if (!t.getDirectAllowed()) {
        throw new DirectoryValidationException("directory.error.packageNotDirect");
      }
    }

    LabPackage p =
        id == null ? new LabPackage() : packages.findById(id).orElseThrow(this::notFound);
    p.setName(name.trim());
    p.setHospitalId(hospitalId);
    p.setPrice(price);
    p.setActive(active);
    p = packages.save(p);

    jdbc.deletePackageItems(p.getId());
    for (UUID testId : unique) {
      LabPackageItem item = new LabPackageItem();
      item.setPackageId(p.getId());
      item.setTestId(testId);
      packageItems.save(item);
    }
    audit(
        "DIRECTORY_PACKAGE_SAVE",
        "lab_package",
        p.getId(),
        Map.of("name", p.getName(), "items", unique.size()));
    return p;
  }

  // ---- helpers -------------------------------------------------------------

  private void audit(String action, String resourceType, UUID resourceId, Map<String, Object> details) {
    audit.record(currentUser.requireUserId(), null, action, resourceType, resourceId, details);
  }

  private DirectoryValidationException notFound() {
    return new DirectoryValidationException("directory.error.notFound");
  }

  private static void requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new DirectoryValidationException("directory.error.blankName");
    }
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
