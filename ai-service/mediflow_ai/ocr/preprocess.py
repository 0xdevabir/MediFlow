"""Stage 1: OpenCV pre-processing — deskew, denoise, contrast, binarise."""

from __future__ import annotations

import cv2
import numpy as np


def decode_image(data: bytes) -> np.ndarray:
    """Decode image bytes to BGR uint8. Raises ValueError if undecodable."""
    arr = np.frombuffer(data, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("undecodable image")
    return img


def _deskew(gray: np.ndarray) -> np.ndarray:
    """Estimate skew from the dominant text line angle and rotate."""
    thr = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)[1]
    coords = np.column_stack(np.where(thr > 0))
    if coords.size < 100:
        return gray
    angle = cv2.minAreaRect(coords)[-1]
    if angle < -45:
        angle = 90 + angle
    if abs(angle) < 0.3 or abs(angle) > 15:
        return gray
    h, w = gray.shape[:2]
    m = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1.0)
    return cv2.warpAffine(gray, m, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)


def preprocess(image: np.ndarray) -> np.ndarray:
    """Return a single-channel uint8 image ready for OCR.

    Steps: grayscale → deskew → bilateral denoise → CLAHE contrast → Otsu binarise.
    """
    if image.ndim == 3:
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    else:
        gray = image.copy()
    # Upscale tiny phone crops so OCR has enough pixels
    h, w = gray.shape[:2]
    if max(h, w) < 900:
        scale = 900 / max(h, w)
        gray = cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    gray = _deskew(gray)
    gray = cv2.bilateralFilter(gray, d=5, sigmaColor=50, sigmaSpace=50)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    gray = clahe.apply(gray)
    binary = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]
    return binary


def image_quality_ok(binary: np.ndarray, *, min_ink_ratio: float = 0.005, max_ink_ratio: float = 0.55) -> bool:
    """Cheap gate: too blank or too black → likely unreadable."""
    ink = 1.0 - (float(np.mean(binary)) / 255.0)
    return min_ink_ratio <= ink <= max_ink_ratio
