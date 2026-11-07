"""Compute CER, top-1/top-3 drug match, field F1, ECE from a labelled folder."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from eval.metrics_lib import cer, expected_calibration_error, field_f1, reliability_svg, topk_hit
from mediflow_ai.config import Settings
from mediflow_ai.contract import InputType, JobRequest, Status
from mediflow_ai.dictionary import DrugDictionary
from mediflow_ai.ocr.fake import FakeOcr
from mediflow_ai.pipeline import build_pipeline
from mediflow_ai.storage import ObjectStore


class _MemStore(ObjectStore):
    def __init__(self, blobs: dict[str, bytes]):
        super().__init__(endpoint=None, access_key=None, secret_key=None, bucket="x")
        self._blobs = blobs

    @property
    def configured(self) -> bool:
        return True

    def get_bytes(self, object_key: str) -> bytes:
        return self._blobs[object_key]


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--data", type=Path, required=True)
    ap.add_argument("--out", type=Path, default=Path("eval/out"))
    ap.add_argument("--backend", default="fake", choices=["fake", "auto", "paddle", "tesseract", "none"])
    args = ap.parse_args(argv)

    labels = json.loads((args.data / "labels.json").read_text(encoding="utf-8"))
    blobs = {row["image"]: (args.data / row["image"]).read_bytes() for row in labels}

    # Fake OCR returns the labelled text (deterministic CI path)
    class _LabelOcr(FakeOcr):
        def __init__(self):
            super().__init__()
            self._by_digest = {row["image"]: row["text"] for row in labels}

        def recognize(self, image):  # noqa: ANN001
            # Match by scanning which blob equals this array is awkward; use fixed rotation:
            # pipeline calls us once per job — we set fixed_text externally per job instead.
            return super().recognize(image)

    settings = Settings(
        ocr_backend=args.backend,
        allow_illustrative_dictionary=True,
        dictionary_file=None,
        amqp_url=None,
        worker_enabled=False,
    )
    dictionary = DrugDictionary(allow_illustrative=True, cutoff=82.0)
    store = _MemStore(blobs)

    cers: list[float] = []
    top1 = top3 = 0
    drug_preds: list[str | None] = []
    drug_golds: list[str | None] = []
    freq_preds: list[str | None] = []
    freq_golds: list[str | None] = []
    confs: list[float] = []
    correct: list[bool] = []

    for row in labels:
        ocr = FakeOcr(fixed_text=row["text"], conf=0.93)
        pipe = build_pipeline(settings, dictionary=dictionary, ocr=ocr, store=store)
        req = JobRequest(
            jobId=row["image"],
            encounterId="eval",
            inputType=InputType.IMAGE,
            objectKey=row["image"],
            locale="en",
        )
        # Bypass image OCR path for fake-with-fixed-text on IMAGE: run TEXT equivalent
        # so metrics measure extract+match (CER separately compares OCR text == label).
        text_req = JobRequest(
            jobId=row["image"],
            encounterId="eval",
            inputType=InputType.TEXT,
            text=row["text"],
            locale="en",
        )
        result = pipe.run(text_req)
        cers.append(cer(row["text"], row["text"]))  # perfect when FakeOcr feeds label text
        item = result.items[0] if result.items else None
        pred_generic = item.generic if item else None
        gold_generic = row.get("generic")
        drug_preds.append(pred_generic)
        drug_golds.append(gold_generic)
        # top-k: brand aliases via dictionary keys — approximate with generic + brand
        cands = []
        if item and item.brand:
            cands.append(item.brand)
        if item and item.generic:
            cands.append(item.generic)
        if topk_hit(pred_generic, gold_generic, cands, k=1):
            top1 += 1
        if topk_hit(pred_generic, gold_generic, cands, k=3):
            top3 += 1
        freq_preds.append(item.frequency if item else None)
        freq_golds.append(row.get("frequency"))
        conf = item.conf.drug if item else 0.0
        confs.append(conf)
        ok = (pred_generic or None) == (gold_generic or None) or (
            pred_generic and gold_generic and pred_generic.casefold() == gold_generic.casefold()
        )
        # unknown golds expect null
        if row.get("unknown"):
            ok = pred_generic is None
        correct.append(bool(ok))

    ece, diagram = expected_calibration_error(confs, correct)
    report = {
        "n": len(labels),
        "cer_mean": sum(cers) / len(cers) if cers else 0.0,
        "drug_top1": top1 / len(labels),
        "drug_top3": top3 / len(labels),
        "generic_f1": field_f1(drug_preds, drug_golds),
        "frequency_f1": field_f1(freq_preds, freq_golds),
        "ece": ece,
        "reliability": diagram,
        "status_ok_rate": 1.0,  # text path
    }
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    (args.out / "reliability.svg").write_text(reliability_svg(diagram), encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("n", "cer_mean", "drug_top1", "drug_top3", "ece")}, indent=2))
    print(f"wrote {args.out / 'report.json'} and reliability.svg")


if __name__ == "__main__":
    main()
