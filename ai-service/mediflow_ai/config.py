"""Runtime configuration from ``MEDIFLOW_AI_*`` environment variables (no secrets in code)."""

from __future__ import annotations

import os
from dataclasses import dataclass


def _env(name: str, default: str | None = None) -> str | None:
    value = os.environ.get(name)
    return value if value not in (None, "") else default


def _bool(name: str, default: bool) -> bool:
    value = _env(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _float(name: str, default: float) -> float:
    value = _env(name)
    return float(value) if value is not None else default


def _int(name: str, default: int) -> int:
    value = _env(name)
    return int(value) if value is not None else default


@dataclass(frozen=True)
class Settings:
    # RabbitMQ worker
    amqp_url: str | None = None
    worker_enabled: bool = True
    prefetch: int = 4
    job_timeout_s: float = 8.0  # Java gives up at 10 s
    worker_threads: int = 4

    # MinIO / S3 (prescription photos)
    s3_endpoint: str | None = None  # host:port
    s3_access_key: str | None = None
    s3_secret_key: str | None = None
    s3_bucket: str = "prescriptions"
    s3_secure: bool = False
    max_image_bytes: int = 15 * 1024 * 1024

    # Dictionary (brand -> generic -> ATC)
    dictionary_url: str | None = None  # e.g. http://backend:8080/api/internal/dictionary/export
    dictionary_token: str | None = None
    dictionary_token_header: str = "X-Internal-Token"
    dictionary_file: str | None = None  # local JSON or CSV export
    dictionary_refresh_s: int = 3600
    allow_illustrative_dictionary: bool = True

    # Models
    ocr_backend: str = "auto"  # auto | paddle | tesseract | fake | none
    calibrator_path: str | None = "models/calibrator.json"
    match_cutoff: float = 82.0  # RapidFuzz ratio (0-100) below which a name is "unknown"

    # UNREADABLE gates
    min_ocr_chars: int = 8
    min_ocr_mean_conf: float = 0.45

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            amqp_url=_env("MEDIFLOW_AI_AMQP_URL"),
            worker_enabled=_bool("MEDIFLOW_AI_WORKER_ENABLED", True),
            prefetch=_int("MEDIFLOW_AI_PREFETCH", 4),
            job_timeout_s=_float("MEDIFLOW_AI_JOB_TIMEOUT_S", 8.0),
            worker_threads=_int("MEDIFLOW_AI_WORKER_THREADS", 4),
            s3_endpoint=_env("MEDIFLOW_AI_S3_ENDPOINT"),
            s3_access_key=_env("MEDIFLOW_AI_S3_ACCESS_KEY"),
            s3_secret_key=_env("MEDIFLOW_AI_S3_SECRET_KEY"),
            s3_bucket=_env("MEDIFLOW_AI_S3_BUCKET", "prescriptions"),
            s3_secure=_bool("MEDIFLOW_AI_S3_SECURE", False),
            max_image_bytes=_int("MEDIFLOW_AI_MAX_IMAGE_BYTES", 15 * 1024 * 1024),
            dictionary_url=_env("MEDIFLOW_AI_DICTIONARY_URL"),
            dictionary_token=_env("MEDIFLOW_AI_DICTIONARY_TOKEN"),
            dictionary_token_header=_env("MEDIFLOW_AI_DICTIONARY_TOKEN_HEADER", "X-Internal-Token"),
            dictionary_file=_env("MEDIFLOW_AI_DICTIONARY_FILE"),
            dictionary_refresh_s=_int("MEDIFLOW_AI_DICTIONARY_REFRESH_S", 3600),
            allow_illustrative_dictionary=_bool("MEDIFLOW_AI_ALLOW_ILLUSTRATIVE_DICTIONARY", True),
            ocr_backend=_env("MEDIFLOW_AI_OCR_BACKEND", "auto"),
            calibrator_path=_env("MEDIFLOW_AI_CALIBRATOR_PATH", "models/calibrator.json"),
            match_cutoff=_float("MEDIFLOW_AI_MATCH_CUTOFF", 82.0),
            min_ocr_chars=_int("MEDIFLOW_AI_MIN_OCR_CHARS", 8),
            min_ocr_mean_conf=_float("MEDIFLOW_AI_MIN_OCR_MEAN_CONF", 0.45),
        )
