package com.mediflow.directory.repo;

import com.mediflow.directory.api.DirectoryViews.DirectTest;
import com.mediflow.directory.api.DirectoryViews.SearchCriteria;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC access for the directory read model (joins across hospital/department/practitioner/role/slot)
 * and for the V200 extension columns the T00 entities do not map.
 */
@Repository
public class DirectoryJdbc {

  public static final int MAX_DOCTORS = 100;

  private final NamedParameterJdbcTemplate jdbc;

  public DirectoryJdbc(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ---------------------------------------------------------------- rows

  public record HospitalRow(
      UUID id,
      String name,
      String type,
      String address,
      String phone,
      String emergencyPhone,
      String ambulancePhone,
      String openingHours,
      boolean active,
      UUID parentId,
      String parentName,
      Instant lastUpdated) {}

  public record DepartmentRow(
      UUID id, UUID hospitalId, String hospitalName, String name, boolean clinical) {}

  public record PractitionerRow(
      UUID id, UUID userId, String name, String roleLabel, String licenceNo, String specialty) {}

  public record DoctorRow(
      UUID roleId,
      UUID practitionerId,
      String name,
      String roleLabel,
      String specialty,
      UUID hospitalId,
      String hospitalName,
      UUID departmentId,
      String departmentName,
      boolean active,
      String schedule) {}

  public record SlotRow(
      UUID id, UUID roleId, Instant startsAt, Instant endsAt, int capacity, int bookedCount) {}

  public record PackageItemRow(UUID packageId, DirectTest test) {}

  public record PackageRow(
      UUID id, String name, UUID hospitalId, String hospitalName, BigDecimal price, boolean active) {}

  // ---------------------------------------------------------------- hospitals

  private static final String HOSPITAL_SELECT =
      "SELECT h.id, h.name, h.type, h.address, h.phone, h.emergency_phone, h.ambulance_phone,"
          + " h.opening_hours, h.active, h.parent_hospital_id, ph.name AS parent_name,"
          + " GREATEST(h.updated_at,"
          + "   COALESCE((SELECT max(d.updated_at) FROM department d WHERE d.hospital_id = h.id), h.updated_at),"
          + "   COALESCE((SELECT max(r.updated_at) FROM practitioner_role r WHERE r.hospital_id = h.id), h.updated_at)"
          + " ) AS last_updated"
          + " FROM hospital h LEFT JOIN hospital ph ON ph.id = h.parent_hospital_id";

  private static final String HOSPITAL_ORDER =
      " ORDER BY COALESCE(ph.name, h.name), (h.parent_hospital_id IS NOT NULL), h.name";

  public List<HospitalRow> allHospitals() {
    return jdbc.query(HOSPITAL_SELECT + HOSPITAL_ORDER, DirectoryJdbc::hospital);
  }

  public Optional<HospitalRow> hospital(UUID id) {
    return jdbc
        .query(HOSPITAL_SELECT + " WHERE h.id = :id", new MapSqlParameterSource("id", id), DirectoryJdbc::hospital)
        .stream()
        .findFirst();
  }

  /** Public hospital search: active only, branch-aware, clinical departments only. */
  public List<HospitalRow> searchHospitals(SearchCriteria c) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder(HOSPITAL_SELECT).append(" WHERE h.active");
    if (c.hospitalId() != null) {
      sql.append(" AND (h.id = :hid OR h.parent_hospital_id = :hid)");
      p.addValue("hid", c.hospitalId());
    }
    if (c.normalizedDepartment() != null) {
      sql.append(
          " AND EXISTS (SELECT 1 FROM department d WHERE d.hospital_id = h.id AND d.clinical"
              + " AND lower(d.name) = lower(:dept))");
      p.addValue("dept", c.normalizedDepartment());
    }
    if (c.normalizedQuery() != null) {
      sql.append(
          " AND (h.name ILIKE :q OR h.address ILIKE :q OR ph.name ILIKE :q"
              + " OR EXISTS (SELECT 1 FROM department d WHERE d.hospital_id = h.id AND d.clinical AND d.name ILIKE :q)"
              + " OR EXISTS (SELECT 1 FROM practitioner_role r JOIN practitioner pr ON pr.id = r.practitioner_id"
              + "   JOIN department d2 ON d2.id = r.department_id"
              + "   WHERE r.hospital_id = h.id AND r.active AND d2.clinical"
              + "   AND (pr.name ILIKE :q OR pr.specialty ILIKE :q)))");
      p.addValue("q", like(c.normalizedQuery()));
    }
    sql.append(HOSPITAL_ORDER);
    return jdbc.query(sql.toString(), p, DirectoryJdbc::hospital);
  }

  public void updateHospitalExtras(UUID id, UUID parentId, String ambulancePhone) {
    jdbc.update(
        "UPDATE hospital SET parent_hospital_id = :parent, ambulance_phone = :amb WHERE id = :id",
        new MapSqlParameterSource("id", id).addValue("parent", parentId).addValue("amb", ambulancePhone));
  }

  /** True when {@code candidateParent} is {@code id} or one of its descendants (cycle check). */
  public boolean isSelfOrDescendant(UUID id, UUID candidateParent) {
    Boolean b =
        jdbc.queryForObject(
            "WITH RECURSIVE tree(id) AS (SELECT CAST(:id AS uuid) UNION"
                + " SELECT h.id FROM hospital h JOIN tree t ON h.parent_hospital_id = t.id)"
                + " SELECT EXISTS (SELECT 1 FROM tree WHERE id = :cand)",
            new MapSqlParameterSource("id", id).addValue("cand", candidateParent),
            Boolean.class);
    return Boolean.TRUE.equals(b);
  }

  // ---------------------------------------------------------------- departments

  private static final String DEPT_SELECT =
      "SELECT d.id, d.hospital_id, h.name AS hospital_name, d.name, d.clinical"
          + " FROM department d JOIN hospital h ON h.id = d.hospital_id";

  public List<DepartmentRow> allDepartments() {
    return jdbc.query(DEPT_SELECT + " ORDER BY h.name, d.name", DirectoryJdbc::department);
  }

  public List<DepartmentRow> clinicalDepartments(Collection<UUID> hospitalIds) {
    if (hospitalIds.isEmpty()) {
      return List.of();
    }
    return jdbc.query(
        DEPT_SELECT + " WHERE d.clinical AND d.hospital_id IN (:ids) ORDER BY d.name",
        new MapSqlParameterSource("ids", hospitalIds),
        DirectoryJdbc::department);
  }

  public Optional<DepartmentRow> department(UUID id) {
    return jdbc
        .query(DEPT_SELECT + " WHERE d.id = :id", new MapSqlParameterSource("id", id), DirectoryJdbc::department)
        .stream()
        .findFirst();
  }

  /** Distinct clinical department names at active hospitals (public filter list). */
  public List<String> clinicalDepartmentNames() {
    return jdbc.queryForList(
        "SELECT DISTINCT d.name FROM department d JOIN hospital h ON h.id = d.hospital_id"
            + " WHERE d.clinical AND h.active ORDER BY d.name",
        new MapSqlParameterSource(),
        String.class);
  }

  /** Distinct specialties for active practitioners with a clinical role (public filter list). */
  public List<String> specialtyNames() {
    return jdbc.queryForList(
        "SELECT DISTINCT p.specialty FROM practitioner p"
            + " JOIN practitioner_role r ON r.practitioner_id = p.id"
            + " JOIN department d ON d.id = r.department_id"
            + " JOIN hospital h ON h.id = r.hospital_id"
            + " WHERE r.active AND h.active AND d.clinical"
            + " AND p.specialty IS NOT NULL AND btrim(p.specialty) <> ''"
            + " ORDER BY p.specialty",
        new MapSqlParameterSource(),
        String.class);
  }

  public void updateDepartmentClinical(UUID id, boolean clinical) {
    jdbc.update(
        "UPDATE department SET clinical = :c WHERE id = :id",
        new MapSqlParameterSource("id", id).addValue("c", clinical));
  }

  // ---------------------------------------------------------------- practitioners

  public List<PractitionerRow> allPractitioners() {
    return jdbc.query(
        "SELECT id, user_id, name, role_label, licence_no, specialty FROM practitioner ORDER BY name",
        DirectoryJdbc::practitioner);
  }

  public Optional<PractitionerRow> practitioner(UUID id) {
    return jdbc
        .query(
            "SELECT id, user_id, name, role_label, licence_no, specialty FROM practitioner WHERE id = :id",
            new MapSqlParameterSource("id", id),
            DirectoryJdbc::practitioner)
        .stream()
        .findFirst();
  }

  public void updatePractitionerSpecialty(UUID id, String specialty) {
    jdbc.update(
        "UPDATE practitioner SET specialty = :s WHERE id = :id",
        new MapSqlParameterSource("id", id).addValue("s", specialty));
  }

  // ---------------------------------------------------------------- doctor roles

  private static final String DOCTOR_SELECT =
      "SELECT r.id AS role_id, p.id AS prac_id, p.name, p.role_label, p.specialty,"
          + " h.id AS hid, h.name AS hname, d.id AS did, d.name AS dname, r.active, r.schedule"
          + " FROM practitioner_role r JOIN practitioner p ON p.id = r.practitioner_id"
          + " JOIN hospital h ON h.id = r.hospital_id JOIN department d ON d.id = r.department_id";

  public List<DoctorRow> allDoctorRoles() {
    return jdbc.query(DOCTOR_SELECT + " ORDER BY p.name, h.name", DirectoryJdbc::doctor);
  }

  public Optional<DoctorRow> doctorRole(UUID roleId) {
    return jdbc
        .query(DOCTOR_SELECT + " WHERE r.id = :id", new MapSqlParameterSource("id", roleId), DirectoryJdbc::doctor)
        .stream()
        .findFirst();
  }

  /**
   * Public doctor search: active roles at active hospitals in clinical departments. When {@code
   * dayFrom/dayTo} are given only doctors with a free slot in that window (after {@code now}) match.
   */
  public List<DoctorRow> searchDoctors(SearchCriteria c, Instant dayFrom, Instant dayTo, Instant now) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    StringBuilder sql =
        new StringBuilder(DOCTOR_SELECT).append(" WHERE r.active AND h.active AND d.clinical");
    if (c.hospitalId() != null) {
      sql.append(" AND (h.id = :hid OR h.parent_hospital_id = :hid)");
      p.addValue("hid", c.hospitalId());
    }
    if (c.normalizedDepartment() != null) {
      sql.append(" AND lower(d.name) = lower(:dept)");
      p.addValue("dept", c.normalizedDepartment());
    }
    if (c.normalizedSpecialty() != null) {
      sql.append(" AND lower(p.specialty) = lower(:specialty)");
      p.addValue("specialty", c.normalizedSpecialty());
    }
    if (c.normalizedQuery() != null) {
      sql.append(
          " AND (p.name ILIKE :q OR p.specialty ILIKE :q OR d.name ILIKE :q OR h.name ILIKE :q)");
      p.addValue("q", like(c.normalizedQuery()));
    }
    if (dayFrom != null && dayTo != null) {
      sql.append(
          " AND EXISTS (SELECT 1 FROM slot s WHERE s.practitioner_role_id = r.id"
              + " AND s.starts_at >= :from AND s.starts_at < :to AND s.starts_at > :now"
              + " AND s.capacity > s.booked_count)");
      p.addValue("from", ts(dayFrom)).addValue("to", ts(dayTo)).addValue("now", ts(now));
    }
    sql.append(" ORDER BY p.name, h.name LIMIT ").append(MAX_DOCTORS);
    return jdbc.query(sql.toString(), p, DirectoryJdbc::doctor);
  }

  /** Free slots for many roles in [from, to), after {@code now}; at most {@code perRole} each. */
  public List<SlotRow> freeSlots(
      Collection<UUID> roleIds, Instant from, Instant to, Instant now, int perRole) {
    if (roleIds.isEmpty()) {
      return List.of();
    }
    return jdbc.query(
        "SELECT id, practitioner_role_id, starts_at, ends_at, capacity, booked_count FROM ("
            + " SELECT s.*, row_number() OVER (PARTITION BY s.practitioner_role_id ORDER BY s.starts_at) AS rn"
            + " FROM slot s WHERE s.practitioner_role_id IN (:ids)"
            + " AND s.starts_at >= :from AND s.starts_at < :to AND s.starts_at > :now"
            + " AND s.capacity > s.booked_count) x WHERE rn <= :per ORDER BY starts_at",
        new MapSqlParameterSource("ids", roleIds)
            .addValue("from", ts(from))
            .addValue("to", ts(to))
            .addValue("now", ts(now))
            .addValue("per", perRole),
        (rs, i) ->
            new SlotRow(
                rs.getObject("id", UUID.class),
                rs.getObject("practitioner_role_id", UUID.class),
                instant(rs, "starts_at"),
                instant(rs, "ends_at"),
                rs.getInt("capacity"),
                rs.getInt("booked_count")));
  }

  // ---------------------------------------------------------------- lab tests / packages

  public List<DirectTest> directTests() {
    return jdbc.query(
        "SELECT id, code, name, loinc_code, price FROM lab_test WHERE direct_allowed AND active ORDER BY name",
        DirectoryJdbc::test);
  }

  public List<PackageRow> packages(boolean activeOnly, UUID hospitalId) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    StringBuilder sql =
        new StringBuilder(
            "SELECT lp.id, lp.name, lp.hospital_id, h.name AS hname, lp.price, lp.active"
                + " FROM lab_package lp LEFT JOIN hospital h ON h.id = lp.hospital_id WHERE TRUE");
    if (activeOnly) {
      sql.append(" AND lp.active");
    }
    if (hospitalId != null) {
      sql.append(" AND (lp.hospital_id IS NULL OR lp.hospital_id = :hid)");
      p.addValue("hid", hospitalId);
    }
    sql.append(" ORDER BY lp.name");
    return jdbc.query(
        sql.toString(),
        p,
        (rs, i) ->
            new PackageRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getObject("hospital_id", UUID.class),
                rs.getString("hname"),
                rs.getBigDecimal("price"),
                rs.getBoolean("active")));
  }

  public List<PackageItemRow> packageItems(Collection<UUID> packageIds) {
    if (packageIds.isEmpty()) {
      return List.of();
    }
    return jdbc.query(
        "SELECT i.package_id, t.id, t.code, t.name, t.loinc_code, t.price FROM lab_package_item i"
            + " JOIN lab_test t ON t.id = i.test_id WHERE i.package_id IN (:ids) ORDER BY t.name",
        new MapSqlParameterSource("ids", packageIds),
        (rs, i) -> new PackageItemRow(rs.getObject("package_id", UUID.class), test(rs, i)));
  }

  /** Number of packages that contain the test. */
  public int packagesContaining(UUID testId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM lab_package_item WHERE test_id = :id",
            new MapSqlParameterSource("id", testId),
            Integer.class);
    return n == null ? 0 : n;
  }

  public void deletePackageItems(UUID packageId) {
    jdbc.update(
        "DELETE FROM lab_package_item WHERE package_id = :id",
        new MapSqlParameterSource("id", packageId));
  }

  public boolean labTestCodeTaken(String code, UUID excludingId) {
    MapSqlParameterSource p = new MapSqlParameterSource("code", code);
    String sql = "SELECT EXISTS (SELECT 1 FROM lab_test WHERE lower(code) = lower(:code)";
    if (excludingId != null) {
      sql += " AND id <> :id";
      p.addValue("id", excludingId);
    }
    sql += ")";
    Boolean b = jdbc.queryForObject(sql, p, Boolean.class);
    return Boolean.TRUE.equals(b);
  }

  // ---------------------------------------------------------------- mapping helpers

  static String like(String q) {
    String escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  private static OffsetDateTime ts(Instant i) {
    return i.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toInstant();
  }

  private static HospitalRow hospital(ResultSet rs, int i) throws SQLException {
    return new HospitalRow(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getString("type"),
        rs.getString("address"),
        rs.getString("phone"),
        rs.getString("emergency_phone"),
        rs.getString("ambulance_phone"),
        rs.getString("opening_hours"),
        rs.getBoolean("active"),
        rs.getObject("parent_hospital_id", UUID.class),
        rs.getString("parent_name"),
        instant(rs, "last_updated"));
  }

  private static DepartmentRow department(ResultSet rs, int i) throws SQLException {
    return new DepartmentRow(
        rs.getObject("id", UUID.class),
        rs.getObject("hospital_id", UUID.class),
        rs.getString("hospital_name"),
        rs.getString("name"),
        rs.getBoolean("clinical"));
  }

  private static PractitionerRow practitioner(ResultSet rs, int i) throws SQLException {
    return new PractitionerRow(
        rs.getObject("id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getString("name"),
        rs.getString("role_label"),
        rs.getString("licence_no"),
        rs.getString("specialty"));
  }

  private static DoctorRow doctor(ResultSet rs, int i) throws SQLException {
    return new DoctorRow(
        rs.getObject("role_id", UUID.class),
        rs.getObject("prac_id", UUID.class),
        rs.getString("name"),
        rs.getString("role_label"),
        rs.getString("specialty"),
        rs.getObject("hid", UUID.class),
        rs.getString("hname"),
        rs.getObject("did", UUID.class),
        rs.getString("dname"),
        rs.getBoolean("active"),
        rs.getString("schedule"));
  }

  private static DirectTest test(ResultSet rs, int i) throws SQLException {
    return new DirectTest(
        rs.getObject("id", UUID.class),
        rs.getString("code"),
        rs.getString("name"),
        rs.getString("loinc_code"),
        rs.getBigDecimal("price"));
  }

  /** Unused-import guard for ArrayList in future helpers. */
  @SuppressWarnings("unused")
  private static <T> List<T> mutable() {
    return new ArrayList<>();
  }
}
