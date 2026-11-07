import io

from PIL import Image, ImageDraw

from mediflow_ai.calibrate import Calibrator
from mediflow_ai.config import Settings
from mediflow_ai.contract import InputType, JobRequest, Status, validate_result_dict
from mediflow_ai.dictionary import DrugDictionary
from mediflow_ai.ocr.fake import FakeOcr
from mediflow_ai.pipeline import build_pipeline
from mediflow_ai.storage import ObjectStore, StorageError


class MemStore(ObjectStore):
    def __init__(self, blobs: dict[str, bytes]):
        super().__init__(endpoint=None, access_key=None, secret_key=None, bucket="t")
        self._blobs = blobs

    @property
    def configured(self) -> bool:
        return True

    def get_bytes(self, object_key: str) -> bytes:
        if object_key not in self._blobs:
            raise StorageError("missing")
        return self._blobs[object_key]


def _png(text: str = "Tab Napa 500mg") -> bytes:
    img = Image.new("RGB", (400, 80), "white")
    draw = ImageDraw.Draw(img)
    draw.text((10, 20), text, fill="black")
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def _pipe(ocr_text: str | None = None, **settings_over):
    s = Settings(
        ocr_backend="fake",
        allow_illustrative_dictionary=True,
        worker_enabled=False,
        min_ocr_chars=8,
        min_ocr_mean_conf=0.45,
        **settings_over,
    )
    ocr = FakeOcr(fixed_text=ocr_text or "Tab Napa 500mg 1+0+1 x 5 days", conf=0.95)
    store = MemStore({"rx.png": _png()})
    return build_pipeline(
        s,
        dictionary=DrugDictionary(allow_illustrative=True),
        ocr=ocr,
        store=store,
        calibrator=Calibrator(),
    )


def test_text_napa_end_to_end():
    pipe = _pipe()
    res = pipe.run(
        JobRequest(
            jobId="j1",
            encounterId="e1",
            inputType=InputType.TEXT,
            text="Tab. Napa 500mg 1+0+1 x 5 days",
            locale="en",
        )
    )
    validate_result_dict(res.to_json_dict())
    assert res.status is Status.OK
    assert len(res.items) == 1
    item = res.items[0]
    assert item.brand == "Napa"
    assert item.generic == "Paracetamol"
    assert item.atc == "N02BE01"
    assert item.frequency == "1+0+1"
    assert item.duration == "5 days"
    assert item.conf.drug >= 0.85
    assert item.conf.frequency >= 0.85


def test_unknown_brand_null_conf_zero():
    pipe = _pipe()
    res = pipe.run(
        JobRequest(
            jobId="j2",
            encounterId="e1",
            inputType=InputType.TEXT,
            text="Tab Xyzzol 10mg OD x 5 days",
            locale="en",
        )
    )
    assert res.status is Status.OK
    assert res.items
    item = res.items[0]
    assert item.generic is None and item.brand is None and item.atc is None
    assert item.conf.drug == 0.0
    assert item.frequency == "OD"


def test_image_path_with_fake_ocr():
    pipe = _pipe(ocr_text="Cap Seclo 20mg BD x 14 days")
    res = pipe.run(
        JobRequest(
            jobId="j3",
            encounterId="e1",
            inputType=InputType.IMAGE,
            objectKey="rx.png",
            locale="en",
        )
    )
    validate_result_dict(res.to_json_dict())
    assert res.status is Status.OK
    assert res.items[0].generic == "Omeprazole"
    assert res.items[0].frequency == "BD"
    assert "ocr" in res.timings_ms and "preprocess" in res.timings_ms


def test_unreadable_low_ocr_yield():
    pipe = _pipe(ocr_text="hi")  # too few chars
    # Drop the gate by using short text with high conf — min_ocr_chars=8
    res = pipe.run(
        JobRequest(jobId="j4", encounterId="e1", inputType=InputType.IMAGE, objectKey="rx.png", locale="en")
    )
    assert res.status is Status.UNREADABLE
    assert res.items == []


def test_bangla_numerals_in_text_pipeline():
    pipe = _pipe()
    res = pipe.run(
        JobRequest(
            jobId="j5",
            encounterId="e1",
            inputType=InputType.TEXT,
            text="Napa 500mg ১+০+১ x ৭ দিন",
            locale="bn",
        )
    )
    assert res.status is Status.OK
    item = res.items[0]
    assert item.generic == "Paracetamol"
    assert item.frequency == "1+0+1"
    assert item.duration == "7 days"


def test_calibrator_changes_conf(tmp_path):
    cal_path = tmp_path / "cal.json"
    cal_path.write_text('{"x":[0,1],"y":[0,0.5]}', encoding="utf-8")
    pipe = _pipe()
    pipe.calibrator = Calibrator.load(cal_path)
    res = pipe.run(
        JobRequest(
            jobId="j6",
            encounterId="e1",
            inputType=InputType.TEXT,
            text="Tab Napa 500mg OD",
            locale="en",
        )
    )
    # identity would be ~0.95; half-scale calibrator caps near 0.5
    assert res.items[0].conf.drug <= 0.55
