package com.mediflow.clinical.vitals.web;

import com.mediflow.shared.domain.VitalObservation;
import java.math.BigDecimal;
import java.util.List;

/** Builds a simple SVG polyline point list for vitals trends (CSS/SVG sparkline fallback when Chart.js is unavailable). */
public final class TrendSvg {

  private TrendSvg() {}

  public static String polyline(List<VitalObservation> points, int width, int height) {
    if (points == null || points.isEmpty()) {
      return "";
    }
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (VitalObservation p : points) {
      if (p.getValueNumeric() == null) {
        continue;
      }
      double v = p.getValueNumeric().doubleValue();
      min = Math.min(min, v);
      max = Math.max(max, v);
    }
    if (!Double.isFinite(min) || !Double.isFinite(max)) {
      return "";
    }
    if (max <= min) {
      max = min + 1;
    }
    int n = points.size();
    StringBuilder sb = new StringBuilder();
    int pad = 10;
    for (int i = 0; i < n; i++) {
      BigDecimal num = points.get(i).getValueNumeric();
      if (num == null) {
        continue;
      }
      double x = n == 1 ? width / 2.0 : pad + (width - 2.0 * pad) * i / (n - 1.0);
      double yNorm = (num.doubleValue() - min) / (max - min);
      double y = height - pad - yNorm * (height - 2.0 * pad);
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(String.format(java.util.Locale.ROOT, "%.1f,%.1f", x, y));
    }
    return sb.toString();
  }
}
