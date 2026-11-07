"""OCR backends and shared result types."""

from .base import OcrBackend, OcrLine, OcrResult, OcrToken, OcrUnavailable, split_line

__all__ = [
    "OcrBackend",
    "OcrLine",
    "OcrResult",
    "OcrToken",
    "OcrUnavailable",
    "split_line",
]
