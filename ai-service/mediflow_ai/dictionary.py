"""Stage 4: RapidFuzz brand → generic → ATC match against the DGDA export.

No match → brand/generic/atc null, drug confidence 0 (never guess).
Source order: local file → HTTP export → illustrative seed (dev/tests only).
"""

from __future__ import annotations

import csv
import json
import logging
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

import httpx
from rapidfuzz import fuzz, process

log = logging.getLogger("mediflow_ai.dictionary")

# Package-bundled illustrative seed (marked clearly; never used when a real export is available
# unless Settings.allow_illustrative_dictionary is true AND no other source loaded).
_PACKAGE_DATA = Path(__file__).resolve().parent / "data" / "illustrative_dictionary.json"


@dataclass(frozen=True)
class DrugEntry:
    brand: str
    generic: str
    atc: str | None = None
    strength: str | None = None
    form: str | None = None
    aliases: tuple[str, ...] = ()


@dataclass(frozen=True)
class Match:
    entry: DrugEntry | None
    score: float  # 0..1 RapidFuzz ratio of the winning key
    matched_as: str | None = None  # the dictionary key that won


class DrugDictionary:
    """Thread-safe, hourly-refreshable brand dictionary."""

    def __init__(
        self,
        *,
        url: str | None = None,
        token: str | None = None,
        token_header: str = "X-Internal-Token",
        file_path: str | None = None,
        refresh_s: int = 3600,
        allow_illustrative: bool = True,
        cutoff: float = 82.0,
    ):
        self.url = url
        self.token = token
        self.token_header = token_header
        self.file_path = Path(file_path) if file_path else None
        self.refresh_s = refresh_s
        self.allow_illustrative = allow_illustrative
        self.cutoff = cutoff
        self._lock = threading.RLock()
        self._entries: list[DrugEntry] = []
        self._keys: list[str] = []  # parallel to choices for RapidFuzz
        self._by_key: dict[str, DrugEntry] = {}
        self._loaded_at = 0.0
        self._source = "empty"
        self.reload(force=True)

    @property
    def source(self) -> str:
        return self._source

    @property
    def size(self) -> int:
        with self._lock:
            return len(self._entries)

    def maybe_refresh(self) -> None:
        if self.refresh_s <= 0:
            return
        if time.monotonic() - self._loaded_at < self.refresh_s:
            return
        self.reload(force=True)

    def reload(self, *, force: bool = False) -> None:
        if not force and self._entries:
            return
        entries: list[DrugEntry] = []
        source = "empty"
        if self.file_path and self.file_path.is_file():
            entries = list(_load_file(self.file_path))
            source = f"file:{self.file_path.name}"
        elif self.url:
            try:
                entries = list(_load_http(self.url, self.token, self.token_header))
                source = "http"
            except Exception as exc:  # noqa: BLE001 — fall through; never crash the worker
                log.warning("dictionary_http_failed", extra={"err": type(exc).__name__})
        if not entries and self.allow_illustrative and _PACKAGE_DATA.is_file():
            entries = list(_load_file(_PACKAGE_DATA))
            source = "illustrative"
        self._install(entries, source)

    def _install(self, entries: list[DrugEntry], source: str) -> None:
        keys: list[str] = []
        by_key: dict[str, DrugEntry] = {}
        for e in entries:
            for raw in (e.brand, *e.aliases):
                key = _norm_key(raw)
                if not key or key in by_key:
                    continue
                by_key[key] = e
                keys.append(key)
            # also index bare generic so "paracetamol 500" can match
            gkey = _norm_key(e.generic)
            if gkey and gkey not in by_key:
                by_key[gkey] = e
                keys.append(gkey)
        with self._lock:
            self._entries = entries
            self._keys = keys
            self._by_key = by_key
            self._loaded_at = time.monotonic()
            self._source = source
        log.info("dictionary_loaded", extra={"source": source, "entries": len(entries), "keys": len(keys)})

    def match(self, name: str, *, strength: str | None = None) -> Match:
        """Fuzzy-match a drug name. score is 0..1; below cutoff → empty Match."""
        self.maybe_refresh()
        key = _norm_key(name)
        if not key:
            return Match(None, 0.0)
        with self._lock:
            keys = self._keys
            by_key = self._by_key
        if not keys:
            return Match(None, 0.0)
        # Prefer exact / casefold hit
        if key in by_key:
            return Match(by_key[key], 1.0, key)
        # Try "name + strength number" (Napa 500)
        if strength:
            num = "".join(c for c in strength if c.isdigit() or c == ".")
            if num:
                combo = _norm_key(f"{name} {num}")
                if combo in by_key:
                    return Match(by_key[combo], 1.0, combo)
        hit = process.extractOne(
            key,
            keys,
            scorer=fuzz.WRatio,
            score_cutoff=self.cutoff,
        )
        if not hit:
            return Match(None, 0.0)
        matched_key, score100, _ = hit
        return Match(by_key[matched_key], float(score100) / 100.0, matched_key)


def _norm_key(s: str) -> str:
    return " ".join("".join(ch.lower() if ch.isalnum() or ch.isspace() else " " for ch in s).split())


def _load_file(path: Path) -> Iterable[DrugEntry]:
    text = path.read_text(encoding="utf-8")
    if path.suffix.lower() == ".csv":
        yield from _from_rows(csv.DictReader(text.splitlines()))
        return
    data = json.loads(text)
    if isinstance(data, dict) and "entries" in data:
        data = data["entries"]
    if not isinstance(data, list):
        raise ValueError("dictionary JSON must be a list or {entries: [...]}")
    yield from _from_rows(data)


def _load_http(url: str, token: str | None, header: str) -> Iterable[DrugEntry]:
    headers = {}
    if token:
        headers[header] = token
    with httpx.Client(timeout=10.0) as client:
        r = client.get(url, headers=headers)
        r.raise_for_status()
        ctype = r.headers.get("content-type", "")
        if "csv" in ctype or url.endswith(".csv"):
            yield from _from_rows(csv.DictReader(r.text.splitlines()))
            return
        data = r.json()
        if isinstance(data, dict) and "entries" in data:
            data = data["entries"]
        yield from _from_rows(data)


def _from_rows(rows: Iterable[Any]) -> Iterable[DrugEntry]:
    for row in rows:
        if not isinstance(row, dict):
            continue
        brand = (row.get("brand") or row.get("brandName") or row.get("name") or "").strip()
        generic = (row.get("generic") or row.get("genericName") or "").strip()
        if not brand or not generic:
            continue
        atc = (row.get("atc") or row.get("atcCode") or None) or None
        if atc is not None:
            atc = str(atc).strip() or None
        strength = (row.get("strength") or None) or None
        form = (row.get("form") or None) or None
        aliases_raw = row.get("aliases") or row.get("alias") or []
        if isinstance(aliases_raw, str):
            aliases = tuple(a.strip() for a in aliases_raw.split("|") if a.strip())
        else:
            aliases = tuple(str(a).strip() for a in aliases_raw if str(a).strip())
        yield DrugEntry(
            brand=brand,
            generic=generic,
            atc=atc,
            strength=str(strength).strip() if strength else None,
            form=str(form).strip() if form else None,
            aliases=aliases,
        )
