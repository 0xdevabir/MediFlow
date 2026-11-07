package com.mediflow.lab;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.lab.repo.LabCatalogQueries;
import com.mediflow.lab.repo.LabEncounterQueries;
import com.mediflow.lab.repo.LabHospitalQueries;
import com.mediflow.lab.repo.LabOrderDetailRepository;
import com.mediflow.lab.repo.LabPackageQueries;
import com.mediflow.lab.service.LabAccess;
import com.mediflow.lab.service.LabOrderService;
import com.mediflow.lab.service.LabRuleException;
import com.mediflow.lab.service.LabSlots;
import com.mediflow.shared.domain.Hospital;
import com.mediflow.shared.domain.LabTest;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.LabOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
class LabOrderServiceTest {

  @Mock LabOrderRepository orders;
  @Mock LabOrderDetailRepository details;
  @Mock LabCatalogQueries tests;
  @Mock LabPackageQueries packages;
  @Mock LabEncounterQueries encounters;
  @Mock LabHospitalQueries hospitals;
  @Mock LabAccess access;
  @Mock ApplicationEventPublisher events;
  @Mock AuditPort audit;
  @Mock ClockPort clock;

  LabOrderService service;

  @BeforeEach
  void setUp() {
    service =
        new LabOrderService(
            orders, details, tests, packages, encounters, hospitals, access, events, audit, clock);
  }

  @Test
  void rejectsNonDirectTestForDirectBooking() {
    UUID hospitalId = UUID.randomUUID();
    UUID testId = UUID.randomUUID();
    Hospital hospital = new Hospital();
    hospital.setId(hospitalId);
    hospital.setActive(true);
    hospital.setName("Demo");
    LabTest creat = new LabTest();
    creat.setId(testId);
    creat.setCode("CREAT");
    creat.setName("Creatinine");
    creat.setActive(true);
    creat.setDirectAllowed(false);
    creat.setPrice(new BigDecimal("350"));

    when(hospitals.findById(hospitalId)).thenReturn(Optional.of(hospital));
    when(tests.findById(testId)).thenReturn(Optional.of(creat));
    when(access.requireCurrentPatientId()).thenReturn(UUID.randomUUID());
    when(access.requireUserId()).thenReturn(UUID.randomUUID());
    when(clock.now()).thenReturn(Instant.parse("2026-11-07T10:00:00Z"));

    LocalDate date = LocalDate.now(LabSlots.CLINIC_ZONE).plusDays(1);
    assertThatThrownBy(
            () ->
                service.bookDirectForSelf(
                    hospitalId, List.of(testId), List.of(), date, LabSlots.WINDOWS.get(0)))
        .isInstanceOf(LabRuleException.class)
        .extracting(ex -> ((LabRuleException) ex).getMessageKey())
        .isEqualTo("lab.error.notDirectAllowed");

    verify(orders, never()).save(any());
  }
}
