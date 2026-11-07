package com.mediflow.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mediflow.lab.domain.LabOrderDetail;
import com.mediflow.lab.repo.LabCatalogQueries;
import com.mediflow.lab.repo.LabCriticalTaskRepository;
import com.mediflow.lab.repo.LabOrderDetailRepository;
import com.mediflow.lab.repo.LabOrderQueries;
import com.mediflow.lab.repo.LabPackageQueries;
import com.mediflow.lab.repo.LabPatientQueries;
import com.mediflow.lab.repo.LabReferenceRangeRepository;
import com.mediflow.lab.repo.LabReportQueries;
import com.mediflow.lab.repo.LabResultValueRepository;
import com.mediflow.lab.repo.LabSampleQueries;
import com.mediflow.lab.repo.LabStaffQueries;
import com.mediflow.lab.service.LabAccess;
import com.mediflow.lab.service.LabQueryService;
import com.mediflow.lab.service.LabViews;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.LabTest;
import com.mediflow.shared.domain.OrderType;
import com.mediflow.shared.port.ObjectStoragePort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabTechVisibilityTest {

  @Mock LabOrderQueries orders;
  @Mock LabOrderDetailRepository details;
  @Mock LabReportQueries reports;
  @Mock LabResultValueRepository resultValues;
  @Mock LabReferenceRangeRepository ranges;
  @Mock LabSampleQueries samples;
  @Mock LabCatalogQueries tests;
  @Mock LabPackageQueries packages;
  @Mock LabPatientQueries patients;
  @Mock LabCriticalTaskRepository criticalTasks;
  @Mock LabStaffQueries staff;
  @Mock LabAccess access;
  @Mock ObjectStoragePort storage;

  LabQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new LabQueryService(
            orders,
            details,
            reports,
            resultValues,
            ranges,
            samples,
            tests,
            packages,
            patients,
            criticalTasks,
            staff,
            access,
            storage);
  }

  @Test
  void techQueueHidesOrdersAssignedToSomeoneElse() {
    UUID me = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    UUID hospitalId = UUID.randomUUID();
    UUID mineId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    UUID openId = UUID.randomUUID();
    UUID testId = UUID.randomUUID();

    LabOrder mine = order(mineId, hospitalId, testId);
    LabOrder theirs = order(otherId, hospitalId, testId);
    LabOrder open = order(openId, hospitalId, testId);

    LabOrderDetail dMine = detail(mineId, me);
    LabOrderDetail dTheirs = detail(otherId, other);
    LabOrderDetail dOpen = detail(openId, null);

    LabTest test = new LabTest();
    test.setId(testId);
    test.setName("FBS");
    test.setCode("FBS");
    test.setActive(true);

    when(access.requireUserId()).thenReturn(me);
    when(access.isAdmin()).thenReturn(false);
    when(staff.hospitalIdsOf(me)).thenReturn(List.of(hospitalId));
    when(orders.findByHospitalIdAndStatusInOrderByOrderedAtAsc(any(), any()))
        .thenReturn(List.of(mine, theirs, open));
    when(details.findByLabOrderIdIn(any())).thenReturn(List.of(dMine, dTheirs, dOpen));
    when(reports.findByLabOrderIdIn(any())).thenReturn(List.of());
    when(tests.findByIdIn(any())).thenReturn(List.of(test));
    when(patients.findById(any())).thenReturn(java.util.Optional.empty());

    List<LabViews.OrderRow> rows = service.techQueue(null);

    assertThat(rows).extracting(LabViews.OrderRow::orderId).containsExactlyInAnyOrder(mineId, openId);
    assertThat(rows).extracting(LabViews.OrderRow::orderId).doesNotContain(otherId);
  }

  private static LabOrder order(UUID id, UUID hospitalId, UUID testId) {
    LabOrder o = new LabOrder();
    o.setId(id);
    o.setHospitalId(hospitalId);
    o.setPatientId(UUID.randomUUID());
    o.setTestId(testId);
    o.setStatus(LabOrderStatus.ORDERED);
    o.setOrderType(OrderType.DIRECT);
    o.setOrderedAt(Instant.parse("2026-11-07T08:00:00Z"));
    return o;
  }

  private static LabOrderDetail detail(UUID orderId, UUID techId) {
    LabOrderDetail d = new LabOrderDetail();
    d.setLabOrderId(orderId);
    d.setAssignedTechId(techId);
    d.setBookingRef("LB-Q");
    d.setQrToken("t");
    return d;
  }
}

