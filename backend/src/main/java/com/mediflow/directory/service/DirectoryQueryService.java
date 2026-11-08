package com.mediflow.directory.service;

import com.mediflow.directory.api.DirectoryViews.DirectTest;
import com.mediflow.directory.api.DirectoryViews.DoctorCard;
import com.mediflow.directory.api.DirectoryViews.FreeSlot;
import com.mediflow.directory.api.DirectoryViews.HospitalCard;
import com.mediflow.directory.api.DirectoryViews.HospitalOption;
import com.mediflow.directory.api.DirectoryViews.SearchCriteria;
import com.mediflow.directory.api.DirectoryViews.SearchResult;
import com.mediflow.directory.api.DirectoryViews.TestPackage;
import com.mediflow.directory.config.DirectoryProperties;
import com.mediflow.directory.repo.DirectoryJdbc;
import com.mediflow.directory.repo.DirectoryJdbc.DoctorRow;
import com.mediflow.directory.repo.DirectoryJdbc.HospitalRow;
import com.mediflow.directory.repo.DirectoryJdbc.PackageItemRow;
import com.mediflow.directory.repo.DirectoryJdbc.PackageRow;
import com.mediflow.directory.repo.DirectoryJdbc.SlotRow;
import com.mediflow.directory.repo.DirectorySlotRepository;
import com.mediflow.shared.domain.Slot;
import com.mediflow.shared.port.ClockPort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public (and T04) read API for the hospital / doctor directory. No authentication required for
 * these methods — controllers decide who can call them.
 */
@Service
public class DirectoryQueryService {

  public static final int FREE_SLOTS_PER_CARD = 5;

  private final DirectoryJdbc jdbc;
  private final DirectorySlotRepository slots;
  private final ClockPort clock;
  private final DirectoryProperties props;

  public DirectoryQueryService(
      DirectoryJdbc jdbc,
      DirectorySlotRepository slots,
      ClockPort clock,
      DirectoryProperties props) {
    this.jdbc = jdbc;
    this.slots = slots;
    this.clock = clock;
    this.props = props;
  }

  @Transactional(readOnly = true)
  public SearchResult search(SearchCriteria criteria) {
    SearchCriteria c = criteria == null ? SearchCriteria.all() : criteria;
    ZoneId zone = props.zoneId();
    Instant now = clock.now();
    LocalDate day = c.date() != null ? c.date() : null;
    Instant dayFrom = null;
    Instant dayTo = null;
    if (day != null) {
      dayFrom = day.atStartOfDay(zone).toInstant();
      dayTo = day.plusDays(1).atStartOfDay(zone).toInstant();
    }

    List<HospitalRow> hospitals = jdbc.searchHospitals(c);
    List<DoctorRow> doctors = jdbc.searchDoctors(c, dayFrom, dayTo, now);

    Instant slotFrom =
        dayFrom != null ? dayFrom : LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant();
    Instant slotTo =
        dayTo != null
            ? dayTo
            : LocalDate.ofInstant(now, zone).plusDays(7).atStartOfDay(zone).toInstant();

    Map<UUID, List<FreeSlot>> freeByRole = loadFreeSlots(
        doctors.stream().map(DoctorRow::roleId).toList(), slotFrom, slotTo, now, FREE_SLOTS_PER_CARD);

    Map<UUID, List<String>> deptsByHospital = new LinkedHashMap<>();
    for (var d :
        jdbc.clinicalDepartments(hospitals.stream().map(HospitalRow::id).toList())) {
      deptsByHospital.computeIfAbsent(d.hospitalId(), k -> new ArrayList<>()).add(d.name());
    }

    List<HospitalCard> hospitalCards =
        hospitals.stream().map(h -> toHospitalCard(h, zone, deptsByHospital)).toList();
    List<DoctorCard> doctorCards =
        doctors.stream()
            .map(
                d ->
                    new DoctorCard(
                        d.roleId(),
                        d.practitionerId(),
                        d.name(),
                        d.roleLabel(),
                        d.specialty(),
                        d.hospitalId(),
                        d.hospitalName(),
                        d.departmentId(),
                        d.departmentName(),
                        freeByRole.getOrDefault(d.roleId(), List.of())))
            .toList();
    return new SearchResult(hospitalCards, doctorCards);
  }

  /**
   * Free slots for one practitioner role on a calendar day (Asia/Dhaka by default). Used by T04
   * booking.
   */
  @Transactional(readOnly = true)
  public List<FreeSlot> freeSlots(UUID practitionerRoleId, LocalDate date) {
    if (practitionerRoleId == null || date == null) {
      return List.of();
    }
    ZoneId zone = props.zoneId();
    Instant now = clock.now();
    Instant from = date.atStartOfDay(zone).toInstant();
    Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
    return slots.findFree(practitionerRoleId, from, to, now).stream()
        .map(s -> toFreeSlot(s, zone))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<HospitalOption> hospitalOptions() {
    return jdbc.allHospitals().stream()
        .filter(HospitalRow::active)
        .map(
            h ->
                new HospitalOption(
                    h.id(),
                    h.parentName() == null ? h.name() : h.parentName() + " — " + h.name()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<String> clinicalDepartmentNames() {
    return jdbc.clinicalDepartmentNames();
  }

  @Transactional(readOnly = true)
  public List<String> specialtyNames() {
    return jdbc.specialtyNames();
  }

  @Transactional(readOnly = true)
  public List<DirectTest> directTests() {
    return jdbc.directTests();
  }

  @Transactional(readOnly = true)
  public List<TestPackage> packages(UUID hospitalId) {
    List<PackageRow> rows = jdbc.packages(true, hospitalId);
    Map<UUID, List<DirectTest>> items =
        jdbc.packageItems(rows.stream().map(PackageRow::id).toList()).stream()
            .collect(
                Collectors.groupingBy(
                    PackageItemRow::packageId,
                    Collectors.mapping(PackageItemRow::test, Collectors.toList())));
    return rows.stream()
        .map(
            p ->
                new TestPackage(
                    p.id(),
                    p.name(),
                    p.hospitalId(),
                    p.hospitalName(),
                    p.price(),
                    items.getOrDefault(p.id(), List.of())))
        .toList();
  }

  private Map<UUID, List<FreeSlot>> loadFreeSlots(
      List<UUID> roleIds, Instant from, Instant to, Instant now, int perRole) {
    Map<UUID, List<FreeSlot>> out = new LinkedHashMap<>();
    ZoneId zone = props.zoneId();
    for (SlotRow s : jdbc.freeSlots(roleIds, from, to, now, perRole)) {
      out.computeIfAbsent(s.roleId(), k -> new ArrayList<>())
          .add(
              new FreeSlot(
                  s.id(),
                  s.roleId(),
                  s.startsAt(),
                  s.endsAt(),
                  LocalDateTime.ofInstant(s.startsAt(), zone),
                  LocalDateTime.ofInstant(s.endsAt(), zone),
                  s.capacity() - s.bookedCount()));
    }
    return out;
  }

  private static HospitalCard toHospitalCard(
      HospitalRow h, ZoneId zone, Map<UUID, List<String>> deptsByHospital) {
    Instant updated = h.lastUpdated();
    return new HospitalCard(
        h.id(),
        h.name(),
        h.parentId(),
        h.parentName(),
        h.type(),
        h.address(),
        h.openingHours(),
        h.phone(),
        h.emergencyPhone(),
        h.ambulancePhone(),
        updated,
        updated == null ? null : LocalDate.ofInstant(updated, zone),
        deptsByHospital.getOrDefault(h.id(), List.of()));
  }

  private static FreeSlot toFreeSlot(Slot s, ZoneId zone) {
    return new FreeSlot(
        s.getId(),
        s.getPractitionerRoleId(),
        s.getStartsAt(),
        s.getEndsAt(),
        LocalDateTime.ofInstant(s.getStartsAt(), zone),
        LocalDateTime.ofInstant(s.getEndsAt(), zone),
        s.getCapacity() - s.getBookedCount());
  }
}
