package com.mediflow.identity.access;

import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.PrescriptionStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

/** Tunables of the scope rules (kept in one place so tests and SQL agree). */
public final class AccessRules {

  /** Clinic-local "today" for the checked-in rule. */
  public static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Dhaka");

  /** Lab orders still handled by the lab. */
  public static final Set<LabOrderStatus> OPEN_LAB_STATUSES =
      EnumSet.of(
          LabOrderStatus.ORDERED,
          LabOrderStatus.SAMPLE_COLLECTED,
          LabOrderStatus.IN_LAB,
          LabOrderStatus.RESULTED);

  /** After verification the lab keeps access this long (corrections / phone-out of criticals). */
  public static final Duration LAB_VERIFIED_GRACE = Duration.ofDays(7);

  /** Signed prescriptions stay dispensable (and visible to pharmacists) this long. */
  public static final Duration PRESCRIPTION_DISPENSE_WINDOW = Duration.ofDays(30);

  public static final Set<PrescriptionStatus> SIGNED_STATUSES =
      EnumSet.of(
          PrescriptionStatus.SIGNED,
          PrescriptionStatus.AMENDED,
          PrescriptionStatus.ENTERED_IN_ERROR);

  private AccessRules() {}

  /** Whether a lab tech may still work on (and read) this particular order. */
  public static boolean labOrderHandled(AccessFacts.LabOrderRef order, Instant now) {
    if (OPEN_LAB_STATUSES.contains(order.status())) {
      return true;
    }
    return order.status() == LabOrderStatus.VERIFIED
        && order.updatedAt() != null
        && order.updatedAt().isAfter(now.minus(LAB_VERIFIED_GRACE));
  }
}
