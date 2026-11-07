package com.mediflow.appointment.queue;

import com.mediflow.shared.domain.QueueStatus;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.domain.VitalClass;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * OPD queue ordering (R&amp;D v1.3 §6.2 "Who is seen first?"). Pure functions, no I/O.
 *
 * <ol>
 *   <li>CRITICAL is <b>never</b> in the OPD queue (Red Alert → ER), whatever its status.
 *   <li>URGENT before NORMAL.
 *   <li>Among URGENT: borderline (or worse) vitals first, then arrival time.
 *   <li>NORMAL: first-come by slot start (arrival time when there is no slot).
 *   <li>Ties: token number.
 * </ol>
 *
 * A nurse override does not use a separate rank: it stores a replacement sort key that places the
 * entry just before/after a neighbour. The entry then keeps that place as others leave or arrive,
 * and the rules above still apply to everybody else.
 */
public final class QueueOrdering {

  /** Smallest step Postgres {@code timestamptz} keeps (microseconds). */
  static final Duration NUDGE = Duration.ofNanos(1_000);

  private QueueOrdering() {}

  /** One queue entry as the ordering sees it. */
  public record Candidate(
      UUID entryId,
      TriageLevel level,
      QueueStatus status,
      VitalClass vitals,
      Instant arrivedAt,
      Instant slotStartsAt,
      int tokenSeq,
      SortKey override) {

    public Candidate {
      Objects.requireNonNull(entryId, "entryId");
      Objects.requireNonNull(arrivedAt, "arrivedAt");
      level = level == null ? TriageLevel.NORMAL : level;
      status = status == null ? QueueStatus.WAITING : status;
    }
  }

  /** bucket 0 = URGENT, 1 = NORMAL; sub 0 = borderline-first (urgent only). */
  public record SortKey(int bucket, int sub, Instant time) implements Comparable<SortKey> {

    private static final Comparator<SortKey> ORDER =
        Comparator.comparingInt(SortKey::bucket)
            .thenComparingInt(SortKey::sub)
            .thenComparing(SortKey::time);

    @Override
    public int compareTo(SortKey o) {
      return ORDER.compare(this, o);
    }
  }

  /** True when the entry may appear in the OPD waiting list at all. */
  public static boolean isOpdEligible(Candidate c) {
    return c.level() != TriageLevel.CRITICAL && c.status() == QueueStatus.WAITING;
  }

  /** The key the §6.2 rules give, ignoring any override. */
  public static SortKey ruleKey(Candidate c) {
    if (c.level() == TriageLevel.URGENT) {
      boolean borderlineOrWorse =
          c.vitals() == VitalClass.BORDERLINE || c.vitals() == VitalClass.CRITICAL;
      return new SortKey(0, borderlineOrWorse ? 0 : 1, c.arrivedAt());
    }
    Instant byslot = c.slotStartsAt() != null ? c.slotStartsAt() : c.arrivedAt();
    return new SortKey(1, 0, byslot);
  }

  public static SortKey effectiveKey(Candidate c) {
    return c.override() != null ? c.override() : ruleKey(c);
  }

  private static final Comparator<Candidate> COMPARATOR =
      Comparator.comparing(QueueOrdering::effectiveKey)
          .thenComparingInt(Candidate::tokenSeq)
          .thenComparing(Candidate::entryId);

  /** Waiting OPD list in call order. CRITICAL and non-waiting entries are dropped. */
  public static List<Candidate> order(Collection<Candidate> all) {
    return all.stream().filter(QueueOrdering::isOpdEligible).sorted(COMPARATOR).toList();
  }

  /** Number of waiting patients ahead of {@code entryId}; -1 when it is not in the OPD list. */
  public static int aheadOf(Collection<Candidate> all, UUID entryId) {
    List<Candidate> ordered = order(all);
    for (int i = 0; i < ordered.size(); i++) {
      if (ordered.get(i).entryId().equals(entryId)) {
        return i;
      }
    }
    return -1;
  }

  /** Override key that puts an entry immediately before {@code target}. */
  public static SortKey placeBefore(Candidate target) {
    SortKey k = effectiveKey(target);
    return new SortKey(k.bucket(), k.sub(), k.time().minus(NUDGE));
  }

  /** Override key that puts an entry immediately after {@code target}. */
  public static SortKey placeAfter(Candidate target) {
    SortKey k = effectiveKey(target);
    return new SortKey(k.bucket(), k.sub(), k.time().plus(NUDGE));
  }
}
