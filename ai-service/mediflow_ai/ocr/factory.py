"""Select an OCR backend from settings (auto → paddle → tesseract → none)."""

from __future__ import annotations

import logging

from .base import OcrBackend, OcrUnavailable
from .fake import FakeOcr

log = logging.getLogger("mediflow_ai.ocr")


class NullOcr:
    """Always returns empty — used when no engine is installed (TEXT jobs still work)."""

    name = "none"

    def recognize(self, image):  # noqa: ANN001
        from .base import OcrResult

        return OcrResult(lines=[], engine=self.name)


def build_ocr(backend: str = "auto") -> OcrBackend:
    choice = (backend or "auto").strip().lower()
    if choice == "fake":
        return FakeOcr()
    if choice == "none":
        return NullOcr()
    if choice == "paddle":
        from .paddle_backend import PaddleOcr

        return PaddleOcr()
    if choice == "tesseract":
        from .tesseract_backend import TesseractOcr

        return TesseractOcr()
    if choice != "auto":
        raise ValueError(f"unknown OCR backend: {backend}")

    for builder in (_try_paddle, _try_tesseract):
        eng = builder()
        if eng is not None:
            log.info("ocr_backend_selected", extra={"engine": eng.name})
            return eng
    log.warning("ocr_backend_none")
    return NullOcr()


def _try_paddle() -> OcrBackend | None:
    try:
        from .paddle_backend import PaddleOcr

        return PaddleOcr()
    except OcrUnavailable as exc:
        log.info("paddle_unavailable", extra={"reason": str(exc)})
        return None


def _try_tesseract() -> OcrBackend | None:
    try:
        from .tesseract_backend import TesseractOcr

        return TesseractOcr()
    except OcrUnavailable as exc:
        log.info("tesseract_unavailable", extra={"reason": str(exc)})
        return None
