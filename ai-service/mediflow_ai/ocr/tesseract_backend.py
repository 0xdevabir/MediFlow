"""Tesseract OCR fallback (optional dependency: pytesseract + system tesseract)."""

from __future__ import annotations

import logging

import numpy as np

from .base import OcrResult, OcrUnavailable, split_line

log = logging.getLogger("mediflow_ai.ocr.tesseract")


class TesseractOcr:
    name = "tesseract"

    def __init__(self, lang: str = "eng"):
        try:
            import pytesseract  # type: ignore
        except ImportError as exc:
            raise OcrUnavailable("pytesseract not installed") from exc
        self._tess = pytesseract
        self.lang = lang
        # Probe the binary early so "auto" can skip a broken install
        try:
            pytesseract.get_tesseract_version()
        except Exception as exc:  # noqa: BLE001 — surface as unavailable
            raise OcrUnavailable(f"tesseract binary missing: {type(exc).__name__}") from exc

    def recognize(self, image: np.ndarray) -> OcrResult:
        data = self._tess.image_to_data(image, lang=self.lang, output_type=self._tess.Output.DICT)
        n = len(data["text"])
        # Group by line number
        by_line: dict[int, list[tuple[str, float]]] = {}
        for i in range(n):
            word = (data["text"][i] or "").strip()
            if not word:
                continue
            conf_raw = float(data["conf"][i])
            if conf_raw < 0:
                continue
            conf = max(0.0, min(1.0, conf_raw / 100.0))
            key = int(data["line_num"][i])
            by_line.setdefault(key, []).append((word, conf))
        lines = []
        for key in sorted(by_line):
            tokens = by_line[key]
            # rebuild via split_line using mean conf if we need a single line object
            from .base import OcrLine, OcrToken

            lines.append(OcrLine([OcrToken(w, c) for w, c in tokens]))
        return OcrResult(lines, engine=self.name)
