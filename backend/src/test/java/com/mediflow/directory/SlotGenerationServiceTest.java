package com.mediflow.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.directory.config.DirectoryProperties;
import com.mediflow.directory.repo.DirectorySlotRepository;
import com.mediflow.directory.service.SlotGenerationService;
import com.mediflow.shared.domain.PractitionerRoleEntity;
import com.mediflow.shared.domain.Slot;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.PractitionerRoleEntityRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlotGenerationServiceTest {

  private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
  // Monday
  private static final Instant NOW =
      LocalDate.of(2026, 11, 9).atTime(8, 0).atZone(DHAKA).toInstant();

  @Mock PractitionerRoleEntityRepository roles;
  @Mock DirectorySlotRepository slots;
  @Mock ClockPort clock;

  SlotGenerationService service;

  @BeforeEach
  void setUp() {
    DirectoryProperties props = new DirectoryProperties("Asia/Dhaka", 2, "0 15 2 * * *", true);
    service = new SlotGenerationService(roles, slots, clock, props);
    when(clock.now()).thenReturn(NOW);
  }

  @Test
  void materialisesSlotsWithoutDuplicatesOnSecondPass() {
    UUID roleId = UUID.randomUUID();
    PractitionerRoleEntity role = new PractitionerRoleEntity();
    role.setId(roleId);
    role.setActive(true);
    role.setSchedule(
        "[{\"day\":\"MONDAY\",\"start\":\"09:00\",\"end\":\"10:00\",\"slotMinutes\":30,\"capacity\":1}]");

    when(slots.findInRange(eq(roleId), any(), any())).thenReturn(List.of());
    when(slots.insertIfAbsent(eq(roleId), any(), any(), anyInt())).thenReturn(1);

    SlotGenerationService.Result first = service.reconcile(role);
    assertThat(first.created()).isEqualTo(2); // 09:00 and 09:30 on Monday within 2-day horizon

    // Second pass: existing slots match plan → no inserts of already-present starts
    Slot existing1 = slot(roleId, "09:00", "09:30", 1, 0);
    Slot existing2 = slot(roleId, "09:30", "10:00", 1, 0);
    when(slots.findInRange(eq(roleId), any(), any())).thenReturn(List.of(existing1, existing2));

    SlotGenerationService.Result second = service.reconcile(role);
    assertThat(second.created()).isZero();
    assertThat(second.removed()).isZero();
    verify(slots, never()).delete(any());
  }

  @Test
  void doesNotDeleteBookedSlotsWhenScheduleShrinks() {
    UUID roleId = UUID.randomUUID();
    PractitionerRoleEntity role = new PractitionerRoleEntity();
    role.setId(roleId);
    role.setActive(true);
    role.setSchedule("[]"); // no windows

    Slot booked = slot(roleId, "09:00", "09:30", 1, 1);
    when(slots.findInRange(eq(roleId), any(), any())).thenReturn(List.of(booked));

    SlotGenerationService.Result result = service.reconcile(role);
    assertThat(result.removed()).isZero();
    assertThat(result.affectedBookedSlotIds()).containsExactly(booked.getId());
    verify(slots, never()).delete(any());
  }

  private static Slot slot(UUID roleId, String start, String end, int capacity, int booked) {
    Slot s = new Slot();
    s.setId(UUID.randomUUID());
    s.setPractitionerRoleId(roleId);
    s.setStartsAt(LocalDate.of(2026, 11, 9).atTime(
            Integer.parseInt(start.substring(0, 2)), Integer.parseInt(start.substring(3)))
        .atZone(DHAKA)
        .toInstant());
    s.setEndsAt(LocalDate.of(2026, 11, 9).atTime(
            Integer.parseInt(end.substring(0, 2)), Integer.parseInt(end.substring(3)))
        .atZone(DHAKA)
        .toInstant());
    s.setCapacity(capacity);
    s.setBookedCount(booked);
    return s;
  }
}
