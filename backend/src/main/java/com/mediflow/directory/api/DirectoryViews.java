package com.mediflow.directory.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Read-only views returned by {@link DirectoryQueryService} (safe for public pages). */
public final class DirectoryViews {

  private DirectoryViews() {}

  /**
   * Search filters; every field optional. {@code department} is a clinical department name; {@code
   * specialty} matches practitioner specialty exactly (case-insensitive).
   */
  public record SearchCriteria(
      String q, UUID hospitalId, String department, String specialty, LocalDate date) {

    public static SearchCriteria all() {
      return new SearchCriteria(null, null, null, null, null);
    }

    public String normalizedQuery() {
      return q == null || q.isBlank() ? null : q.trim();
    }

    public String normalizedDepartment() {
      return department == null || department.isBlank() ? null : department.trim();
    }

    public String normalizedSpecialty() {
      return specialty == null || specialty.isBlank() ? null : specialty.trim();
    }
  }

  public record SearchResult(List<HospitalCard> hospitals, List<DoctorCard> doctors) {}

  /** F2 + F7 hospital card. {@code lastUpdated} = latest change to the hospital, departments or doctor roles. */
  public record HospitalCard(
      UUID id,
      String name,
      UUID parentId,
      String parentName,
      String type,
      String address,
      String openingHours,
      String phone,
      String emergencyPhone,
      String ambulancePhone,
      Instant lastUpdated,
      LocalDate lastUpdatedLocal,
      List<String> clinicalDepartments) {

    public boolean isBranch() {
      return parentId != null;
    }

    /** True when an emergency contact number is published for the card. */
    public boolean hasEmergencyPhone() {
      return emergencyPhone != null && !emergencyPhone.isBlank();
    }
  }

  public record DoctorCard(
      UUID practitionerRoleId,
      UUID practitionerId,
      String name,
      String roleLabel,
      String specialty,
      UUID hospitalId,
      String hospitalName,
      UUID departmentId,
      String departmentName,
      List<FreeSlot> freeSlots) {}

  /**
   * A bookable slot. {@code remaining} = capacity − booked. {@code localStart}/{@code localEnd} are
   * in the directory zone (Asia/Dhaka by default).
   */
  public record FreeSlot(
      UUID slotId,
      UUID practitionerRoleId,
      Instant startsAt,
      Instant endsAt,
      LocalDateTime localStart,
      LocalDateTime localEnd,
      int remaining) {}

  /** A test patients may book without a doctor (F1); price is shown before booking. */
  public record DirectTest(UUID id, String code, String name, String loincCode, BigDecimal price) {

    public boolean hasPrice() {
      return price != null;
    }
  }

  /** F3 bundle of direct tests. {@code hospitalId} null = offered at every hospital. */
  public record TestPackage(
      UUID id,
      String name,
      UUID hospitalId,
      String hospitalName,
      BigDecimal price,
      List<DirectTest> tests) {

    public boolean hasPrice() {
      return price != null;
    }
  }

  public record HospitalOption(UUID id, String label) {}
}
