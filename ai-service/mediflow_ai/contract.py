"""AI service contract (T06 <-> T09), see docs/plan/CONVENTIONS.md.

Request  (ai.rx.requests): {jobId, encounterId, inputType: IMAGE|TEXT, objectKey?, text?, locale}
Result   (ai.rx.results):  {jobId, status: OK|UNREADABLE|ERROR, items:[...], timingsMs:{...}, modelVersion}

Every item key is always present; unknown values are ``null`` with confidence 0.
The JSON Schemas in ``mediflow_ai/schemas/`` are the language-neutral copy of this module.
"""

from __future__ import annotations

import json
from enum import Enum
from functools import lru_cache
from pathlib import Path
from typing import Any

import jsonschema
from pydantic import BaseModel, ConfigDict, Field

SCHEMA_DIR = Path(__file__).resolve().parent / "schemas"

REQUEST_QUEUE = "ai.rx.requests"
RESULT_QUEUE = "ai.rx.results"
DLQ = "ai.rx.dlq"


class InputType(str, Enum):
    IMAGE = "IMAGE"
    TEXT = "TEXT"


class Status(str, Enum):
    OK = "OK"
    UNREADABLE = "UNREADABLE"
    ERROR = "ERROR"


class _Camel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")


class JobRequest(_Camel):
    job_id: str = Field(alias="jobId", min_length=1, max_length=64)
    encounter_id: str = Field(alias="encounterId", min_length=1, max_length=64)
    input_type: InputType = Field(alias="inputType")
    object_key: str | None = Field(default=None, alias="objectKey", max_length=1024)
    text: str | None = Field(default=None, max_length=20000)
    locale: str = Field(default="en", max_length=16)


class FieldConf(_Camel):
    drug: float = 0.0
    dose: float = 0.0
    frequency: float = 0.0
    route: float = 0.0
    duration: float = 0.0


class Item(_Camel):
    raw: str
    brand: str | None = None
    generic: str | None = None
    atc: str | None = None
    strength: str | None = None
    form: str | None = None
    dose: str | None = None
    route: str | None = None
    frequency: str | None = None
    duration: str | None = None
    conf: FieldConf = Field(default_factory=FieldConf)


class JobResult(_Camel):
    job_id: str = Field(alias="jobId")
    status: Status
    items: list[Item] = Field(default_factory=list)
    timings_ms: dict[str, int] = Field(default_factory=dict, alias="timingsMs")
    model_version: str = Field(alias="modelVersion")

    def to_json_dict(self) -> dict[str, Any]:
        return self.model_dump(by_alias=True, mode="json")

    def to_bytes(self) -> bytes:
        return json.dumps(self.to_json_dict(), ensure_ascii=False, separators=(",", ":")).encode("utf-8")


@lru_cache(maxsize=None)
def _schema(name: str) -> dict[str, Any]:
    return json.loads((SCHEMA_DIR / name).read_text(encoding="utf-8"))


def validate_request_dict(payload: dict[str, Any]) -> None:
    """Raise ``jsonschema.ValidationError`` if the payload breaks the request schema."""
    jsonschema.validate(payload, _schema("ai-rx-request.schema.json"))


def validate_result_dict(payload: dict[str, Any]) -> None:
    jsonschema.validate(payload, _schema("ai-rx-result.schema.json"))


class PoisonMessage(ValueError):
    """Message that can never be processed (bad JSON / schema); goes to the DLQ."""

    def __init__(self, reason: str, job_id: str | None = None):
        super().__init__(reason)
        self.reason = reason
        self.job_id = job_id


def parse_request(body: bytes) -> JobRequest:
    try:
        payload = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise PoisonMessage(f"invalid JSON: {type(exc).__name__}") from exc
    if not isinstance(payload, dict):
        raise PoisonMessage("payload is not an object")
    job_id = payload.get("jobId") if isinstance(payload.get("jobId"), str) else None
    try:
        validate_request_dict(payload)
    except jsonschema.ValidationError as exc:
        # message path only (never the offending value, which might contain text)
        path = "/".join(str(p) for p in exc.absolute_path) or "<root>"
        raise PoisonMessage(f"schema violation at {path}: {exc.validator}", job_id) from exc
    return JobRequest.model_validate(payload)
