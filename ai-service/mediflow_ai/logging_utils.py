"""Privacy-safe logging: jobId / timings / model version only — never PHI or free text."""

from __future__ import annotations

import logging
import sys


class _NoPhiFilter(logging.Filter):
    """Drop records that look like they accidentally include clinical text keys."""

    _BLOCK = frozenset({"text", "raw", "patient", "patientid", "name", "phone", "ocr_text"})

    def filter(self, record: logging.LogRecord) -> bool:
        extra = getattr(record, "__dict__", {})
        for key in extra:
            if key.lower() in self._BLOCK:
                return False
        return True


def setup_logging(level: str = "INFO") -> None:
    root = logging.getLogger()
    if root.handlers:
        return
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(name)s %(message)s"))
    handler.addFilter(_NoPhiFilter())
    root.addHandler(handler)
    root.setLevel(level)
    logging.getLogger("mediflow_ai").addFilter(_NoPhiFilter())
