"""OCR interface. Engines (PaddleOCR, Tesseract, fakes) all return ``OcrResult``."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Protocol, runtime_checkable

import numpy as np

from ..numerals import bangla_to_ascii_digits


@dataclass(frozen=True)
class OcrToken:
    text: str
    conf: float  # 0..1


@dataclass
class OcrLine:
    tokens: list[OcrToken] = field(default_factory=list)

    @property
    def text(self) -> str:
        return " ".join(t.text for t in self.tokens)

    def char_confidences(self) -> list[float]:
        """Confidence for every character of ``text`` (separators take the lower neighbour)."""
        out: list[float] = []
        for i, tok in enumerate(self.tokens):
            if i:
                out.append(min(tok.conf, self.tokens[i - 1].conf))
            out.extend([tok.conf] * len(tok.text))
        return out

    @classmethod
    def from_text(cls, text: str, conf: float = 1.0) -> "OcrLine":
        return cls([OcrToken(w, conf) for w in text.split()])


@dataclass
class OcrResult:
    lines: list[OcrLine] = field(default_factory=list)
    engine: str = "none"

    @property
    def char_count(self) -> int:
        return sum(len(t.text) for line in self.lines for t in line.tokens)

    @property
    def mean_conf(self) -> float:
        """Character-weighted mean token confidence (0 if nothing was read)."""
        total = self.char_count
        if not total:
            return 0.0
        return sum(t.conf * len(t.text) for line in self.lines for t in line.tokens) / total

    def normalised(self) -> "OcrResult":
        """Bangla numerals -> ASCII digits, empty tokens dropped (stage 2 post-processing)."""
        lines = []
        for line in self.lines:
            toks = [OcrToken(bangla_to_ascii_digits(t.text).strip(), max(0.0, min(1.0, float(t.conf))))
                    for t in line.tokens]
            toks = [t for t in toks if t.text]
            if toks:
                lines.append(OcrLine(toks))
        return OcrResult(lines, self.engine)

    @classmethod
    def from_text(cls, text: str, conf: float = 1.0, engine: str = "text") -> "OcrResult":
        lines = [OcrLine.from_text(line, conf) for line in text.splitlines() if line.strip()]
        return cls(lines, engine)


@runtime_checkable
class OcrBackend(Protocol):
    name: str

    def recognize(self, image: np.ndarray) -> OcrResult:
        """Read a pre-processed (grayscale/binary uint8) image."""
        ...


class OcrUnavailable(RuntimeError):
    """The requested OCR engine is not installed."""


def split_line(text: str, conf: float) -> OcrLine:
    """Engines that return line-level confidence: give every word the line's confidence."""
    return OcrLine([OcrToken(w, conf) for w in text.split()])
