package com.mediflow.clinical.vitals.domain;

import com.mediflow.shared.domain.VitalClass;
import java.util.Collection;

/** Ordering NORMAL &lt; BORDERLINE &lt; CRITICAL. */
public final class VitalLevels {

  private VitalLevels() {}

  public static int rank(VitalClass c) {
    return switch (c) {
      case NORMAL -> 0;
      case BORDERLINE -> 1;
      case CRITICAL -> 2;
    };
  }

  public static VitalClass max(VitalClass a, VitalClass b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return rank(a) >= rank(b) ? a : b;
  }

  /** Patient level: the highest level among all vitals; NORMAL for an empty set. */
  public static VitalClass max(Collection<VitalClass> levels) {
    VitalClass result = VitalClass.NORMAL;
    for (VitalClass c : levels) {
      result = max(result, c);
    }
    return result;
  }
}
