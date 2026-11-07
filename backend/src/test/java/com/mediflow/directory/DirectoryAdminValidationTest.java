package com.mediflow.directory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.directory.repo.DirectoryJdbc;
import com.mediflow.directory.service.DirectoryAdminService;
import com.mediflow.directory.service.DirectoryValidationException;
import com.mediflow.directory.service.SlotGenerationService;
import com.mediflow.shared.domain.LabTest;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class DirectoryAdminValidationTest {

  @Mock HospitalRepository hospitals;
  @Mock DepartmentRepository departments;
  @Mock PractitionerRepository practitioners;
  @Mock PractitionerRoleEntityRepository roles;
  @Mock LabTestRepository tests;
  @Mock LabPackageRepository packages;
  @Mock LabPackageItemRepository packageItems;
  @Mock DirectoryJdbc jdbc;
  @Mock SlotGenerationService slots;
  @Mock AuditPort audit;
  @Mock ClockPort clock;
  @Mock CurrentUser currentUser;
  @Mock ApplicationEventPublisher events;

  DirectoryAdminService admin;

  @BeforeEach
  void setUp() {
    admin =
        new DirectoryAdminService(
            hospitals,
            departments,
            practitioners,
            roles,
            tests,
            packages,
            packageItems,
            jdbc,
            slots,
            audit,
            clock,
            currentUser,
            events);
    lenient().when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
    lenient().when(clock.now()).thenReturn(Instant.parse("2026-11-07T12:00:00Z"));
  }

  @Test
  void packageRejectsNonDirectTest() {
    UUID testId = UUID.randomUUID();
    LabTest t = new LabTest();
    t.setId(testId);
    t.setDirectAllowed(false);
    when(tests.findById(testId)).thenReturn(Optional.of(t));

    assertThatThrownBy(
            () ->
                admin.savePackage(
                    null, "Bad package", null, new BigDecimal("10"), true, List.of(testId)))
        .isInstanceOf(DirectoryValidationException.class)
        .extracting(ex -> ((DirectoryValidationException) ex).getMessageKey())
        .isEqualTo("directory.error.packageNotDirect");
    verify(packages, never()).save(any());
  }

  @Test
  void cannotClearDirectAllowedWhileInPackage() {
    UUID id = UUID.randomUUID();
    LabTest existing = new LabTest();
    existing.setId(id);
    existing.setCode("CBC");
    existing.setDirectAllowed(true);
    when(tests.findById(id)).thenReturn(Optional.of(existing));
    when(jdbc.labTestCodeTaken("CBC", id)).thenReturn(false);
    when(jdbc.packagesContaining(id)).thenReturn(1);

    assertThatThrownBy(
            () -> admin.saveTest(id, "CBC", "Complete Blood Count", "58410-2", false, BigDecimal.ONE, true))
        .isInstanceOf(DirectoryValidationException.class)
        .extracting(ex -> ((DirectoryValidationException) ex).getMessageKey())
        .isEqualTo("directory.error.testInPackage");
  }

  @Test
  void hospitalParentCycleRejected() {
    UUID id = UUID.randomUUID();
    UUID parent = UUID.randomUUID();
    when(hospitals.findById(id)).thenReturn(Optional.of(new com.mediflow.shared.domain.Hospital()));
    when(jdbc.isSelfOrDescendant(id, parent)).thenReturn(true);

    assertThatThrownBy(
            () ->
                admin.saveHospital(
                    id, "H", null, null, null, null, null, null, parent, true))
        .isInstanceOf(DirectoryValidationException.class)
        .extracting(ex -> ((DirectoryValidationException) ex).getMessageKey())
        .isEqualTo("directory.error.invalidParent");
  }
}
