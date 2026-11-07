package com.mediflow.appointment.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.appointment.queue.QueueOrdering.Candidate;
import com.mediflow.shared.domain.QueueStatus;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.domain.VitalClass;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class QueueOrderingTest {

  private static final Instant T0 = Instant.parse("2026-11-07T03:00:00Z");

  private static Instant t(int minutes) {
    return T0.plusSeconds(minutes * 60L);
  }

  private static Candidate c(
      String name, TriageLevel level, VitalClass vitals, int arrivedMin, Integer slotMin, int token) {
    return new Candidate(
        UUID.nameUUIDFromBytes(name.getBytes()),
        level,
        QueueStatus.WAITING,
        vitals,
        t(arrivedMin),
        slotMin == null ? null : t(slotMin),
        token,
        null);
  }

  private static List<UUID> ids(List<Candidate> cs) {
    return cs.stream().map(Candidate::entryId).toList();
  }

  private static UUID id(String name) {
    return UUID.nameUUIDFromBytes(name.getBytes());
  }

  @Test
  void urgentAlwaysBeforeNormal() {
    Candidate normalEarly = c("n", TriageLevel.NORMAL, VitalClass.NORMAL, 0, 0, 1);
    Candidate urgentLate = c("u", TriageLevel.URGENT, VitalClass.NORMAL, 90, 120, 9);
    assertThat(ids(QueueOrdering.order(List.of(normalEarly, urgentLate))))
        .containsExactly(id("u"), id("n"));
  }

  @Test
  void amongUrgentBorderlineFirstThenArrival() {
    Candidate u1 = c("u1", TriageLevel.URGENT, VitalClass.NORMAL, 0, null, 1);
    Candidate u2 = c("u2", TriageLevel.URGENT, VitalClass.BORDERLINE, 30, null, 2);
    Candidate u3 = c("u3", TriageLevel.URGENT, VitalClass.BORDERLINE, 10, null, 3);
    Candidate u4 = c("u4", TriageLevel.URGENT, null, 5, null, 4);
    assertThat(ids(QueueOrdering.order(List.of(u1, u2, u3, u4))))
        .containsExactly(id("u3"), id("u2"), id("u1"), id("u4"));
  }

  @Test
  void normalIsFifoBySlotNotArrival() {
    Candidate lateArrivalEarlySlot = c("a", TriageLevel.NORMAL, VitalClass.NORMAL, 50, 10, 5);
    Candidate earlyArrivalLateSlot = c("b", TriageLevel.NORMAL, VitalClass.NORMAL, 0, 40, 1);
    assertThat(ids(QueueOrdering.order(List.of(earlyArrivalLateSlot, lateArrivalEarlySlot))))
        .containsExactly(id("a"), id("b"));
  }

  @Test
  void normalIgnoresBorderlineVitals() {
    Candidate borderline = c("b", TriageLevel.NORMAL, VitalClass.BORDERLINE, 0, 30, 2);
    Candidate plain = c("p", TriageLevel.NORMAL, VitalClass.NORMAL, 5, 20, 3);
    assertThat(ids(QueueOrdering.order(List.of(borderline, plain))))
        .containsExactly(id("p"), id("b"));
  }

  @Test
  void normalWithoutSlotUsesArrival() {
    Candidate walkIn = c("w", TriageLevel.NORMAL, null, 15, null, 4);
    Candidate slotted = c("s", TriageLevel.NORMAL, null, 0, 20, 1);
    assertThat(ids(QueueOrdering.order(List.of(slotted, walkIn))))
        .containsExactly(id("w"), id("s"));
  }

  @Test
  void tiesBrokenByToken() {
    Candidate a = c("a", TriageLevel.NORMAL, null, 0, 10, 7);
    Candidate b = c("b", TriageLevel.NORMAL, null, 0, 10, 3);
    assertThat(ids(QueueOrdering.order(List.of(a, b)))).containsExactly(id("b"), id("a"));
  }

  @ParameterizedTest
  @EnumSource(QueueStatus.class)
  void criticalNeverInOpdQueueWhateverStatus(QueueStatus status) {
    Candidate critical =
        new Candidate(
            id("crit"), TriageLevel.CRITICAL, status, VitalClass.CRITICAL, t(0), t(0), 1, null);
    Candidate normal = c("n", TriageLevel.NORMAL, null, 5, 5, 2);
    List<Candidate> ordered = QueueOrdering.order(List.of(critical, normal));
    assertThat(ids(ordered)).doesNotContain(id("crit"));
    assertThat(QueueOrdering.aheadOf(List.of(critical, normal), id("crit"))).isEqualTo(-1);
  }

  @Test
  void criticalWithOverrideStillExcluded() {
    Candidate critical =
        new Candidate(
            id("crit"),
            TriageLevel.CRITICAL,
            QueueStatus.WAITING,
            null,
            t(0),
            null,
            1,
            new QueueOrdering.SortKey(0, 0, t(-100)));
    assertThat(QueueOrdering.order(List.of(critical))).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = QueueStatus.class,
      names = {"CALLED", "IN_SERVICE", "DONE", "LEFT"})
  void onlyWaitingEntriesAreListed(QueueStatus status) {
    Candidate gone =
        new Candidate(id("g"), TriageLevel.URGENT, status, null, t(0), null, 1, null);
    assertThat(QueueOrdering.order(List.of(gone))).isEmpty();
  }

  /** Every pair of (level, vitals) combinations obeys the rule ranking. */
  static Stream<Arguments> pairwise() {
    List<Arguments> out = new ArrayList<>();
    TriageLevel[] levels = {TriageLevel.URGENT, TriageLevel.NORMAL};
    VitalClass[] vitals = {null, VitalClass.NORMAL, VitalClass.BORDERLINE};
    for (TriageLevel la : levels) {
      for (VitalClass va : vitals) {
        for (TriageLevel lb : levels) {
          for (VitalClass vb : vitals) {
            out.add(Arguments.of(la, va, lb, vb));
          }
        }
      }
    }
    return out.stream();
  }

  @ParameterizedTest
  @MethodSource("pairwise")
  void allCombinationsRankedByRules(
      TriageLevel la, VitalClass va, TriageLevel lb, VitalClass vb) {
    // a arrives first and has the earlier slot; b arrives later.
    Candidate a = c("a", la, va, 0, 0, 1);
    Candidate b = c("b", lb, vb, 10, 10, 2);
    List<UUID> order = ids(QueueOrdering.order(List.of(b, a)));
    int rankA = rank(la, va);
    int rankB = rank(lb, vb);
    if (rankB < rankA) {
      assertThat(order).containsExactly(id("b"), id("a"));
    } else {
      assertThat(order).containsExactly(id("a"), id("b"));
    }
  }

  private static int rank(TriageLevel level, VitalClass vitals) {
    if (level == TriageLevel.URGENT) {
      return vitals == VitalClass.BORDERLINE ? 0 : 1;
    }
    return 2;
  }

  @Test
  void overridePlaceBeforeMovesUpAndSticks() {
    Candidate a = c("a", TriageLevel.NORMAL, null, 0, 0, 1);
    Candidate b = c("b", TriageLevel.NORMAL, null, 0, 10, 2);
    Candidate d = c("d", TriageLevel.NORMAL, null, 0, 20, 3);
    Candidate moved =
        new Candidate(
            d.entryId(), d.level(), d.status(), d.vitals(), d.arrivedAt(), d.slotStartsAt(),
            d.tokenSeq(), QueueOrdering.placeBefore(b));
    assertThat(ids(QueueOrdering.order(List.of(a, b, moved))))
        .containsExactly(id("a"), id("d"), id("b"));
    // a leaves: d keeps its place relative to b.
    assertThat(ids(QueueOrdering.order(List.of(b, moved)))).containsExactly(id("d"), id("b"));
  }

  @Test
  void overrideCanLiftNormalAboveUrgent() {
    Candidate urgent = c("u", TriageLevel.URGENT, VitalClass.BORDERLINE, 0, null, 1);
    Candidate normal = c("n", TriageLevel.NORMAL, null, 0, 0, 2);
    Candidate lifted =
        new Candidate(
            normal.entryId(), normal.level(), normal.status(), null, normal.arrivedAt(),
            normal.slotStartsAt(), 2, QueueOrdering.placeBefore(urgent));
    assertThat(ids(QueueOrdering.order(List.of(urgent, lifted))))
        .containsExactly(id("n"), id("u"));
  }

  @Test
  void overridePlaceAfterMovesDown() {
    Candidate a = c("a", TriageLevel.URGENT, null, 0, null, 1);
    Candidate b = c("b", TriageLevel.NORMAL, null, 0, 10, 2);
    Candidate c2 = c("c", TriageLevel.NORMAL, null, 0, 20, 3);
    Candidate down =
        new Candidate(
            a.entryId(), a.level(), a.status(), null, a.arrivedAt(), null, 1,
            QueueOrdering.placeAfter(b));
    assertThat(ids(QueueOrdering.order(List.of(down, b, c2))))
        .containsExactly(id("b"), id("a"), id("c"));
  }

  @Test
  void aheadOfCountsWaitingOnly() {
    Candidate a = c("a", TriageLevel.NORMAL, null, 0, 0, 1);
    Candidate called =
        new Candidate(id("x"), TriageLevel.NORMAL, QueueStatus.CALLED, null, t(0), t(1), 2, null);
    Candidate me = c("me", TriageLevel.NORMAL, null, 0, 30, 3);
    assertThat(QueueOrdering.aheadOf(List.of(a, called, me), id("me"))).isEqualTo(1);
    assertThat(QueueOrdering.aheadOf(List.of(a, called, me), id("a"))).isZero();
  }
}
