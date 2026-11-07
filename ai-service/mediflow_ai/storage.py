"""MinIO / S3 object fetch for IMAGE jobs. No other network egress."""

from __future__ import annotations

import logging
from dataclasses import dataclass

log = logging.getLogger("mediflow_ai.storage")


class StorageError(RuntimeError):
    pass


@dataclass
class ObjectStore:
    endpoint: str | None
    access_key: str | None
    secret_key: str | None
    bucket: str
    secure: bool = False
    max_bytes: int = 15 * 1024 * 1024

    def __post_init__(self) -> None:
        self._client = None
        if self.endpoint and self.access_key and self.secret_key:
            from minio import Minio

            host = self.endpoint.removeprefix("https://").removeprefix("http://")
            self._client = Minio(
                host,
                access_key=self.access_key,
                secret_key=self.secret_key,
                secure=self.secure or self.endpoint.startswith("https://"),
            )

    @property
    def configured(self) -> bool:
        return self._client is not None

    def get_bytes(self, object_key: str) -> bytes:
        if self._client is None:
            raise StorageError("object store not configured")
        if not object_key or ".." in object_key or object_key.startswith("/"):
            raise StorageError("invalid object key")
        try:
            resp = self._client.get_object(self.bucket, object_key)
            try:
                chunks: list[bytes] = []
                total = 0
                for chunk in resp.stream(32 * 1024):
                    total += len(chunk)
                    if total > self.max_bytes:
                        raise StorageError("object too large")
                    chunks.append(chunk)
                return b"".join(chunks)
            finally:
                resp.close()
                resp.release_conn()
        except StorageError:
            raise
        except Exception as exc:  # noqa: BLE001
            raise StorageError(f"get failed: {type(exc).__name__}") from exc
