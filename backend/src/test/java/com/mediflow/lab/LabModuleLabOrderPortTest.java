package com.mediflow.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.lab.domain.LabOrderDetail;
import com.mediflow.lab.repo.LabCatalogQueries;
import com.mediflow.lab.repo.LabEncounterQueries;
import com.mediflow.lab.repo.LabHospitalQueries;
import com.mediflow.lab.repo.LabOrderDetailRepository;
import com.mediflow.lab.repo.LabPackageQueries;
import com.mediflow.lab.service.LabAccess;
import com.mediflow.lab.service.LabModuleLabOrderPort;
import com.mediflow.lab.service.LabOrderService;
import com.mediflow.lab.service.LabRuleException;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.LabTest;
import com.mediflow.shared.domain.OrderType;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.LabOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LabModuleLabOrderPortTest {

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
  LabModuleLabOrderPort port;

  UUID encounterId = UUID.randomUUID();
  UUID patientId = UUID.randomUUID();
  UUID hospitalId = UUID.randomUUID();
  UUID actorId = UUID.randomUUID();
  UUID testId = UUID.randomUUID();
  UUID orderId = UUID.randomUUID();
  Instant now = Instant.parse("2026-01-15T10:00:00Z");

  @BeforeEach
  void setUp() {
    service =
        new LabOrderService(
            orders, details, tests, packages, encounters, hospitals, access, events, audit, clock);
    port = new LabModuleLabOrderPort(service);
  }

  @Test
  void requestOrder_createsRealRowAndNeverReturnsNilUuid() {
    Encounter enc = new Encounter();
    enc.setId(encounterId);
    enc.setPatientId(patientId);
    enc.setHospitalId(hospitalId);
    when(encounters.findById(encounterId)).thenReturn(Optional.of(enc));

    LabTest test = new LabTest();
    test.setId(testId);
    test.setCode("CBC");
    test.setName("Complete Blood Count");
    test.setLoincCode("58410-2");
    test.setActive(true);
    test.setPrice(new BigDecimal("500"));
    when(tests.findByCodeIgnoreCase("CBC")).thenReturn(Optional.of(test));

    when(access.requireUserId()).thenReturn(actorId);
    when(clock.now()).thenReturn(now);
    when(orders.save(any(LabOrder.class)))
        .thenAnswer(
            inv -> {
              LabOrder o = inv.getArgument(0);
              o.setId(orderId);
              return o;
            });
    when(details.save(any(LabOrderDetail.class))).thenAnswer(inv -> inv.getArgument(0));

    UUID id = port.requestOrder(encounterId, patientId, hospitalId, "CBC");

    assertThat(id).isEqualTo(orderId);
    assertThat(id).isNotEqualTo(new UUID(0L, 0L));

    ArgumentCaptor<LabOrder> orderCap = ArgumentCaptor.forClass(LabOrder.class);
    verify(orders).save(orderCap.capture());
    LabOrder saved = orderCap.getValue();
    assertThat(saved.getOrderType()).isEqualTo(OrderType.DOCTOR);
    assertThat(saved.getStatus()).isEqualTo(LabOrderStatus.ORDERED);
    assertThat(saved.getEncounterId()).isEqualTo(encounterId);
    assertThat(saved.getPatientId()).isEqualTo(patientId);
    assertThat(saved.getHospitalId()).isEqualTo(hospitalId);
    assertThat(saved.getTestId()).isEqualTo(testId);

    verify(events).publishEvent(any(DomainEvents.LabOrderCreated.class));
    verify(events).publishEvent(any(DomainEvents.LabOrderRequested.class));
  }

  @Test
  void resolveTest_byLoincAndUniqueName() {
    LabTest t = new LabTest();
    t.setId(testId);
    t.setCode("GLU");
    t.setName("Glucose fasting");
    t.setLoincCode("1558-6");
    t.setActive(true);
    when(tests.findByCodeIgnoreCase("1558-6")).thenReturn(Optional.empty());
    when(tests.findByLoincCodeIgnoreCase("1558-6")).thenReturn(Optional.of(t));
    assertThat(service.resolveTest("1558-6").getId()).isEqualTo(testId);

    when(tests.findByCodeIgnoreCase("Glucose")).thenReturn(Optional.empty());
    when(tests.findByLoincCodeIgnoreCase("Glucose")).thenReturn(Optional.empty());
    when(tests.findFirstByActiveTrueAndNameIgnoreCase("Glucose")).thenReturn(Optional.empty());
    when(tests.findByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc("Glucose"))
        .thenReturn(List.of(t));
    assertThat(service.resolveTest("Glucose").getCode()).isEqualTo("GLU");
  }

  @Test
  void requestOrder_rejectsEncounterMismatch() {
    Encounter enc = new Encounter();
    enc.setId(encounterId);
    enc.setPatientId(UUID.randomUUID());
    enc.setHospitalId(hospitalId);
    when(encounters.findById(encounterId)).thenReturn(Optional.of(enc));
    assertThatThrownBy(() -> port.requestOrder(encounterId, patientId, hospitalId, "CBC"))
        .isInstanceOf(LabRuleException.class)
        .hasMessageContaining("encounterMismatch");
  }
}
