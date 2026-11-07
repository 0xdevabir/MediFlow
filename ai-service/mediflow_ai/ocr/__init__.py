"""OCR backends and shared result types."""

from .base import OcrBackend, OcrLine, OcrResult, OcrToken, OcrUnavailable, split_line
from .factory import build_ocr
from .preprocess import decode_image, preprocess

__all__ = [
    "OcrBackend",
    "OcrLine",
    "OcrResult",
    "OcrToken",
    "OcrUnavailable",
    "build_ocr",
    "decode_image",
    "preprocess",
    "split_line",
]

