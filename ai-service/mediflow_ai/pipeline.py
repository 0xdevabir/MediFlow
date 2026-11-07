"""End-to-end prescription job pipeline (stages 1–5).

IMAGE: preprocess → OCR → extract → normalise → calibrate
TEXT:  starts at extract (stage 3)
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass

from . import __version__
from .calibrate import Calibrator
from .config import Settings
from .contract import FieldConf, InputType, Item, JobRequest, JobResult, Status
from .dictionary import DrugDictionary
from .extract import (
    DOSE_UNIT_FROM_FORM,
    ROUTE_FROM_FORM,
    Field,
    LineEntities,
    extract_line,
)
from .ocr.base import OcrBackend, OcrResult
from .ocr.preprocess import decode_image, image_quality_ok, preprocess
from .storage import ObjectStore, StorageError

log = logging.getLogger("mediflow_ai.pipeline")

MODEL_VERSION = f"mediflow-ai/{__version__}"


@dataclass
class Pipeline:
    settings: Settings
    dictionary: DrugDictionary
    ocr: OcrBackend
    store: ObjectStore
    calibrator: Calibrator

    def run(self, req: JobRequest) -> JobResult:
        t0 = time.perf_counter()
        timings: dict[str, int] = {}
        try:
            if req.input_type is InputType.TEXT:
                ocr = OcrResult.from_text(req.text or "", conf=1.0, engine="text")
                timings["preprocess"] = 0
                timings["ocr"] = 0
            else:
                ocr, img_timings = self._ocr_image(req.object_key or "")
                timings.update(img_timings)
                if ocr.char_count < self.settings.min_ocr_chars or ocr.mean_conf < self.settings.min_ocr_mean_conf:
                    timings["total"] = _ms(t0)
                    log.info(
                        "job_unreadable",
                        extra={"jobId": req.job_id, "chars": ocr.char_count, "meanConf": round(ocr.mean_conf, 3)},
                    )
                    return JobResult(
                        jobId=req.job_id,
                        status=Status.UNREADABLE,
                        items=[],
                        timingsMs=timings,
                        modelVersion=MODEL_VERSION,
                    )

            t_ex = time.perf_counter()
            items = self._extract_and_normalise(ocr)
            timings["extract"] = _ms(t_ex)
            timings["total"] = _ms(t0)
            log.info(
                "job_ok",
                extra={"jobId": req.job_id, "items": len(items), "timingsMs": timings["total"], "modelVersion": MODEL_VERSION},
            )
            return JobResult(
                jobId=req.job_id,
                status=Status.OK,
                items=items,
                timingsMs=timings,
                modelVersion=MODEL_VERSION,
            )
        except Exception as exc:  # noqa: BLE001 — always return a contract result
            timings["total"] = _ms(t0)
            log.exception("job_error", extra={"jobId": req.job_id, "err": type(exc).__name__})
            return JobResult(
                jobId=req.job_id,
                status=Status.ERROR,
                items=[],
                timingsMs=timings,
                modelVersion=MODEL_VERSION,
            )

    def _ocr_image(self, object_key: str) -> tuple[OcrResult, dict[str, int]]:
        timings: dict[str, int] = {}
        t_get = time.perf_counter()
        try:
            data = self.store.get_bytes(object_key)
        except StorageError:
            raise
        timings["fetch"] = _ms(t_get)

        t_pp = time.perf_counter()
        image = decode_image(data)
        binary = preprocess(image)
        timings["preprocess"] = _ms(t_pp)
        if not image_quality_ok(binary):
            return OcrResult(lines=[], engine="quality-gate"), timings

        t_ocr = time.perf_counter()
        result = self.ocr.recognize(binary).normalised()
        timings["ocr"] = _ms(t_ocr)
        timings["ocrEngine"] = 0  # placeholder so schema stays int-valued; engine name is in logs only
        log.info(
            "ocr_done",
            extra={"engine": result.engine, "chars": result.char_count, "meanConf": round(result.mean_conf, 3)},
        )
        # Drop the dummy key — keep timings clean
        timings.pop("ocrEngine", None)
        return result, timings

    def _extract_and_normalise(self, ocr: OcrResult) -> list[Item]:
        items: list[Item] = []
        for line in ocr.lines:
            ent = extract_line(line)
            item = self._line_to_item(ent)
            if item is not None:
                items.append(item)
        return items

    def _line_to_item(self, ent: LineEntities) -> Item | None:
        if ent.is_header:
            return None
        if not ent.candidates and not ent.has_med_signal:
            return None

        brand = generic = atc = None
        drug_conf = 0.0
        matched_strength = ent.strength.value
        ocr_name_conf = 0.0
        raw_name = None

        for cand in ent.candidates:
            strength_hint = ent.strength.value or (f"{ent.strength_number} mg" if ent.strength_number else None)
            m = self.dictionary.match(cand.text, strength=strength_hint)
            if m.entry is None:
                continue
            brand = m.entry.brand
            generic = m.entry.generic
            atc = m.entry.atc
            ocr_name_conf = cand.conf
            drug_conf = ocr_name_conf * m.score
            raw_name = cand.text
            if not matched_strength and m.entry.strength:
                matched_strength = m.entry.strength
            if not ent.form.value and m.entry.form:
                ent.form = Field(m.entry.form, cand.conf * 0.8)
            break

        # Unknown brand: still emit a row if we saw med signals, but drug conf = 0 and generics null
        if brand is None:
            if not ent.has_med_signal and not ent.candidates:
                return None
            raw_name = ent.candidates[0].text if ent.candidates else None
            ocr_name_conf = ent.candidates[0].conf if ent.candidates else 0.0
            drug_conf = 0.0

        # Dose defaults from 1+0+1 slot qty or form
        dose = ent.dose
        if not dose.value and ent.slot_qty is not None:
            unit = DOSE_UNIT_FROM_FORM.get(ent.form.value or "", "tablet")
            qty = ent.slot_qty
            plural = "s" if qty > 1 and unit != "ml" else ""
            from .extract import format_num

            dose = Field(f"{format_num(qty)} {unit}{plural}", ent.slot_conf)
        route = ent.route
        if not route.value and ent.form.value:
            inferred = ROUTE_FROM_FORM.get(ent.form.value)
            if inferred:
                route = Field(inferred, (ent.form.conf or 0.7) * 0.85)

        strength = Field(matched_strength, ent.strength.conf) if matched_strength else ent.strength
        if not strength.value and ent.strength_number:
            strength = Field(f"{ent.strength_number} mg", ocr_name_conf * 0.7)

        cal = self.calibrator
        conf = FieldConf(
            drug=cal(drug_conf),
            dose=cal(dose.conf if dose.value else 0.0),
            frequency=cal(ent.frequency.conf if ent.frequency.value else 0.0),
            route=cal(route.conf if route.value else 0.0),
            duration=cal(ent.duration.conf if ent.duration.value else 0.0),
        )
        return Item(
            raw=ent.raw,
            brand=brand,
            generic=generic,
            atc=atc,
            strength=strength.value,
            form=ent.form.value,
            dose=dose.value,
            route=route.value,
            frequency=ent.frequency.value,
            duration=ent.duration.value,
            conf=conf,
        )


def build_pipeline(
    settings: Settings | None = None,
    *,
    dictionary: DrugDictionary | None = None,
    ocr: OcrBackend | None = None,
    store: ObjectStore | None = None,
    calibrator: Calibrator | None = None,
) -> Pipeline:
    from .ocr.factory import build_ocr

    s = settings or Settings.from_env()
    return Pipeline(
        settings=s,
        dictionary=dictionary
        or DrugDictionary(
            url=s.dictionary_url,
            token=s.dictionary_token,
            token_header=s.dictionary_token_header,
            file_path=s.dictionary_file,
            refresh_s=s.dictionary_refresh_s,
            allow_illustrative=s.allow_illustrative_dictionary,
            cutoff=s.match_cutoff,
        ),
        ocr=ocr or build_ocr(s.ocr_backend),
        store=store
        or ObjectStore(
            endpoint=s.s3_endpoint,
            access_key=s.s3_access_key,
            secret_key=s.s3_secret_key,
            bucket=s.s3_bucket,
            secure=s.s3_secure,
            max_bytes=s.max_image_bytes,
        ),
        calibrator=calibrator or Calibrator.load(s.calibrator_path),
    )


def _ms(t0: float) -> int:
    return max(0, int((time.perf_counter() - t0) * 1000))


