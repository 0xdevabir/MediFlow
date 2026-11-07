"""Stage 5: isotonic confidence calibrator (identity if no file is present)."""

from __future__ import annotations

import json
import logging
from pathlib import Path

log = logging.getLogger("mediflow_ai.calibrate")


class Calibrator:
    """Piecewise-linear isotonic map loaded from ``{"x":[...], "y":[...]}``.

    ``x`` must be non-decreasing in [0, 1]; ``y`` is the calibrated confidence.
    Missing / invalid file → identity.
    """

    def __init__(self, x: list[float] | None = None, y: list[float] | None = None):
        if not x or not y or len(x) != len(y) or len(x) < 2:
            self._x = [0.0, 1.0]
            self._y = [0.0, 1.0]
            self.identity = True
        else:
            self._x = list(x)
            self._y = list(y)
            self.identity = False

    @classmethod
    def load(cls, path: str | Path | None) -> "Calibrator":
        if not path:
            return cls()
        p = Path(path)
        if not p.is_file():
            log.info("calibrator_missing", extra={"path": str(p)})
            return cls()
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
            return cls(data.get("x"), data.get("y"))
        except (OSError, json.JSONDecodeError, TypeError, ValueError) as exc:
            log.warning("calibrator_load_failed", extra={"err": type(exc).__name__})
            return cls()

    def __call__(self, conf: float) -> float:
        c = max(0.0, min(1.0, float(conf)))
        x, y = self._x, self._y
        if c <= x[0]:
            return max(0.0, min(1.0, y[0]))
        if c >= x[-1]:
            return max(0.0, min(1.0, y[-1]))
        for i in range(1, len(x)):
            if c <= x[i]:
                span = x[i] - x[i - 1] or 1e-9
                t = (c - x[i - 1]) / span
                return max(0.0, min(1.0, y[i - 1] + t * (y[i] - y[i - 1])))
        return c
