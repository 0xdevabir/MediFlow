"""Deterministic fake OCR for tests and CI (no model weights)."""

from __future__ import annotations

import hashlib

import numpy as np

from .base import OcrResult


class FakeOcr:
    """Returns a canned prescription text derived from a content hash of the image.

    Tests inject known text via ``FakeOcr(fixed_text=...)``. Production never selects this
    unless ``MEDIFLOW_AI_OCR_BACKEND=fake``.
    """

    name = "fake"

    def __init__(self, fixed_text: str | None = None, conf: float = 0.92):
        self.fixed_text = fixed_text
        self.conf = conf

    def recognize(self, image: np.ndarray) -> OcrResult:
        if self.fixed_text is not None:
            return OcrResult.from_text(self.fixed_text, conf=self.conf, engine=self.name)
        digest = hashlib.sha1(image.tobytes()[:4096]).hexdigest()[:8]
        # Stable but clearly-fake output so accidental prod use is obvious
        text = f"Tab FakeMed-{digest} 500mg 1+0+1 x 5 days"
        return OcrResult.from_text(text, conf=self.conf, engine=self.name)
