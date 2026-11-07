"""PaddleOCR backend (optional dependency: paddleocr + paddlepaddle)."""

from __future__ import annotations

import logging

import numpy as np

from .base import OcrLine, OcrResult, OcrToken, OcrUnavailable, split_line

log = logging.getLogger("mediflow_ai.ocr.paddle")


class PaddleOcr:
    name = "paddle"

    def __init__(self, lang: str = "en"):
        try:
            from paddleocr import PaddleOCR  # type: ignore
        except ImportError as exc:
            raise OcrUnavailable("paddleocr not installed") from exc
        # show_log / use_angle_cls vary by paddleocr version
        try:
            self._ocr = PaddleOCR(use_angle_cls=True, lang=lang, show_log=False)
        except TypeError:
            self._ocr = PaddleOCR(use_angle_cls=True, lang=lang)

    def recognize(self, image: np.ndarray) -> OcrResult:
        # Paddle expects BGR/RGB colour or gray; binary is fine
        raw = self._ocr.ocr(image, cls=True)
        lines: list[OcrLine] = []
        # paddleocr 2.x returns [ [ [box, (text, conf)], ... ] ] or None
        pages = raw or []
        for page in pages:
            if not page:
                continue
            for entry in page:
                if not entry or len(entry) < 2:
                    continue
                text_conf = entry[1]
                if not text_conf:
                    continue
                text, conf = text_conf[0], float(text_conf[1])
                text = (text or "").strip()
                if not text:
                    continue
                conf = max(0.0, min(1.0, conf))
                lines.append(split_line(text, conf))
        return OcrResult(lines, engine=self.name)
