package com.mediflow.directory.seed;

import com.mediflow.directory.repo.DirectoryJdbc;
import com.mediflow.shared.domain.Department;
import com.mediflow.shared.domain.Hospital;
import com.mediflow.shared.domain.LabPackage;
import com.mediflow.shared.domain.LabPackageItem;
import com.mediflow.shared.domain.LabTest;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.domain.PractitionerRoleEntity;
import com.mediflow.shared.repo.AppUserRepository;
import com.mediflow.shared.repo.DepartmentRepository;
import com.mediflow.shared.repo.HospitalRepository;
import com.mediflow.shared.repo.LabPackageItemRepository;
import com.mediflow.shared.repo.LabPackageRepository;
import com.mediflow.shared.repo.LabTestRepository;
import com.mediflow.shared.repo.PractitionerRepository;
import com.mediflow.shared.repo.PractitionerRoleEntityRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev-profile seed: 3 hospitals (incl. a branch), 6+ departments, 10 doctors, direct tests + one
 * basic health-check package. Runs before the directory slot job ({@code @Order(10)}).
 */
@Configuration
@Profile("dev")
public class DirectoryDevSeeder {

  private static final Logger log = LoggerFactory.getLogger(DirectoryDevSeeder.class);
  private static final String MARKER_HOSPITAL = "Dhaka Medical College Hospital";

  private final HospitalRepository hospitals;
  private final DepartmentRepository departments;
  private final PractitionerRepository practitioners;
  private final PractitionerRoleEntityRepository roles;
  private final LabTestRepository tests;
  private final LabPackageRepository packages;
  private final LabPackageItemRepository packageItems;
  private final AppUserRepository users;
  private final DirectoryJdbc jdbc;

  public DirectoryDevSeeder(
      HospitalRepository hospitals,
      DepartmentRepository departments,
      PractitionerRepository practitioners,
      PractitionerRoleEntityRepository roles,
      LabTestRepository tests,
      LabPackageRepository packages,
      LabPackageItemRepository packageItems,
      AppUserRepository users,
      DirectoryJdbc jdbc) {
    this.hospitals = hospitals;
    this.departments = departments;
    this.practitioners = practitioners;
    this.roles = roles;
    this.tests = tests;
    this.packages = packages;
    this.packageItems = packageItems;
    this.users = users;
    this.jdbc = jdbc;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(10)
  @Transactional
  public void seed() {
    boolean already =
        jdbc.allHospitals().stream().anyMatch(h -> MARKER_HOSPITAL.equals(h.name()));
    if (already) {
      log.info("Directory seed already present — skipping");
      return;
    }
    UUID dmch =
        saveHospital(
            MARKER_HOSPITAL,
            "Tertiary",
            "Secretariat Road, Dhaka 1000",
            "02-55165088",
            "999",
            "199",
            "Sat–Thu 08:00–20:00",
            null);
    UUID branch =
        saveHospital(
            "DMCH Outdoor Extension",
            "Branch",
            "Bakshibazar, Dhaka 1211",
            "02-55165089",
            "999",
            "199",
            "Sat–Thu 08:00–14:00",
            dmch);
    UUID birdem =
        saveHospital(
            "BIRDEM General Hospital",
            "Specialist",
            "122 Kazi Nazrul Islam Ave, Dhaka 1000",
            "02-41060501",
            "1066",
            "1066",
            "Sat–Thu 08:00–20:00; Fri 09:00–13:00",
            null);

    UUID dmchCardio = dept(dmch, "Cardiology", true);
    UUID dmchEndo = dept(dmch, "Endocrinology", true);
    UUID dmchMed = dept(dmch, "Medicine", true);
    UUID branchMed = dept(branch, "Medicine", true);
    UUID birdemEndo = dept(birdem, "Endocrinology", true);
    UUID birdemPath = dept(birdem, "Pathology", true);
    dept(dmch, "Administration", false); // non-clinical — hidden from public filters

    seedDoctors(dmch, branch, birdem, dmchCardio, dmchEndo, dmchMed, branchMed, birdemEndo, birdemPath);
    seedTestsAndPackage();
    log.info("Directory seed loaded (3 hospitals, departments, 10 doctors, direct tests + package)");
  }

  private UUID saveHospital(
      String name,
      String type,
      String address,
      String phone,
      String emergency,
      String ambulance,
      String hours,
      UUID parent) {
    Hospital h = new Hospital();
    h.setName(name);
    h.setType(type);
    h.setAddress(address);
    h.setPhone(phone);
    h.setEmergencyPhone(emergency);
    h.setOpeningHours(hours);
    h.setActive(true);
    h = hospitals.save(h);
    jdbc.updateHospitalExtras(h.getId(), parent, ambulance);
    return h.getId();
  }

  private UUID dept(UUID hospitalId, String name, boolean clinical) {
    Department d = new Department();
    d.setHospitalId(hospitalId);
    d.setName(name);
    d = departments.save(d);
    jdbc.updateDepartmentClinical(d.getId(), clinical);
    return d.getId();
  }

  private void seedDoctors(
      UUID dmch,
      UUID branch,
      UUID birdem,
      UUID dmchCardio,
      UUID dmchEndo,
      UUID dmchMed,
      UUID branchMed,
      UUID birdemEndo,
      UUID birdemPath) {
    UUID doctorUserId = users.findByUsername("doctor").map(u -> u.getId()).orElse(null);
    String weekdayAm =
        "[{\"day\":\"MONDAY\",\"start\":\"09:00\",\"end\":\"13:00\",\"slotMinutes\":15,\"capacity\":1},"
            + "{\"day\":\"TUESDAY\",\"start\":\"09:00\",\"end\":\"13:00\",\"slotMinutes\":15,\"capacity\":1},"
            + "{\"day\":\"WEDNESDAY\",\"start\":\"09:00\",\"end\":\"13:00\",\"slotMinutes\":15,\"capacity\":1},"
            + "{\"day\":\"THURSDAY\",\"start\":\"09:00\",\"end\":\"13:00\",\"slotMinutes\":15,\"capacity\":1},"
            + "{\"day\":\"SATURDAY\",\"start\":\"09:00\",\"end\":\"12:00\",\"slotMinutes\":15,\"capacity\":1}]";
    String weekdayPm =
        "[{\"day\":\"MONDAY\",\"start\":\"15:00\",\"end\":\"18:00\",\"slotMinutes\":20,\"capacity\":1},"
            + "{\"day\":\"WEDNESDAY\",\"start\":\"15:00\",\"end\":\"18:00\",\"slotMinutes\":20,\"capacity\":1},"
            + "{\"day\":\"SUNDAY\",\"start\":\"10:00\",\"end\":\"13:00\",\"slotMinutes\":20,\"capacity\":2}]";

    record Doc(String name, String specialty, UUID hospital, UUID dept, String schedule, boolean linkUser) {}
    List<Doc> docs =
        List.of(
            new Doc("Dr. Ayesha Rahman", "Cardiology", dmch, dmchCardio, weekdayAm, true),
            new Doc("Dr. Karim Hossain", "Cardiology", dmch, dmchCardio, weekdayPm, false),
            new Doc("Dr. Nusrat Jahan", "Endocrinology", dmch, dmchEndo, weekdayAm, false),
            new Doc("Dr. Farhan Ahmed", "Internal Medicine", dmch, dmchMed, weekdayPm, false),
            new Doc("Dr. Sabina Yasmin", "Medicine", branch, branchMed, weekdayAm, false),
            new Doc("Dr. Imran Chowdhury", "Medicine", branch, branchMed, weekdayPm, false),
            new Doc("Dr. Laila Kabir", "Diabetes & Endocrinology", birdem, birdemEndo, weekdayAm, false),
            new Doc("Dr. Tanvir Hasan", "Endocrinology", birdem, birdemEndo, weekdayPm, false),
            new Doc("Dr. Mehnaz Sultana", "Clinical Pathology", birdem, birdemPath, weekdayAm, false),
            new Doc("Dr. Rafiqul Islam", "Pathology", birdem, birdemPath, weekdayPm, false));

    int i = 1;
    for (Doc doc : docs) {
      Practitioner p = new Practitioner();
      p.setName(doc.name());
      p.setRoleLabel("Doctor");
      p.setLicenceNo("BMDC-" + (1000 + i));
      if (doc.linkUser() && doctorUserId != null) {
        p.setUserId(doctorUserId);
      }
      p = practitioners.save(p);
      jdbc.updatePractitionerSpecialty(p.getId(), doc.specialty());

      PractitionerRoleEntity role = new PractitionerRoleEntity();
      role.setPractitionerId(p.getId());
      role.setHospitalId(doc.hospital());
      role.setDepartmentId(doc.dept());
      role.setSchedule(doc.schedule());
      role.setActive(true);
      roles.save(role);
      i++;
    }
  }

  private void seedTestsAndPackage() {
    UUID fbs = test("FBS", "Fasting Blood Sugar", "1558-6", "250.00");
    test("RBS", "Random Blood Sugar", "2339-0", "200.00");
    UUID cbc = test("CBC", "Complete Blood Count", "58410-2", "450.00");
    UUID lipid = test("LIPID", "Lipid Profile", "24331-1", "800.00");
    UUID urine = test("URINE-RE", "Urine Routine & Microscopy", "24357-6", "300.00");
    test("HBA1C", "HbA1c", "4548-4", "900.00");

    LabPackage pkg = new LabPackage();
    pkg.setName("Basic Health Check");
    pkg.setHospitalId(null);
    pkg.setPrice(new BigDecimal("1500.00"));
    pkg.setActive(true);
    pkg = packages.save(pkg);
    for (UUID testId : List.of(fbs, cbc, lipid, urine)) {
      LabPackageItem item = new LabPackageItem();
      item.setPackageId(pkg.getId());
      item.setTestId(testId);
      packageItems.save(item);
    }
  }

  private UUID test(String code, String name, String loinc, String price) {
    // The lab catalog migration may already have inserted this code — reuse it.
    var existing = tests.findByCode(code);
    if (existing.isPresent()) {
      return existing.get().getId();
    }
    LabTest t = new LabTest();
    t.setCode(code);
    t.setName(name);
    t.setLoincCode(loinc);
    t.setDirectAllowed(true);
    t.setPrice(new BigDecimal(price));
    t.setActive(true);
    return tests.save(t).getId();
  }
}
