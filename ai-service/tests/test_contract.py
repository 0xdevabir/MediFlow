import json

import jsonschema
import pytest

from mediflow_ai.contract import (
    FieldConf,
    Item,
    JobResult,
    PoisonMessage,
    Status,
    parse_request,
    validate_request_dict,
    validate_result_dict,
)


def _req(**over):
    base = {"jobId": "j-1", "encounterId": "e-1", "inputType": "TEXT", "text": "Tab Napa 500mg 1+0+1", "locale": "en"}
    base.update(over)
    return base


def test_valid_text_request_parses():
    r = parse_request(json.dumps(_req()).encode())
    assert r.job_id == "j-1" and r.input_type.value == "TEXT"


def test_valid_image_request_parses():
    r = parse_request(json.dumps(_req(inputType="IMAGE", text=None, objectKey="rx/abc.jpg")).encode())
    assert r.object_key == "rx/abc.jpg"


@pytest.mark.parametrize(
    "payload",
    [
        b"not json",
        b"[]",
        json.dumps(_req(inputType="PDF")).encode(),
        json.dumps(_req(inputType="IMAGE", text=None)).encode(),  # IMAGE without objectKey
        json.dumps({k: v for k, v in _req().items() if k != "text"}).encode(),  # TEXT without text
        json.dumps(_req(patientName="X")).encode(),  # no patient identifiers allowed
    ],
)
def test_poison_requests_rejected(payload):
    with pytest.raises(PoisonMessage):
        parse_request(payload)


def test_poison_keeps_job_id_but_not_values():
    with pytest.raises(PoisonMessage) as ei:
        parse_request(json.dumps(_req(patientName="Rahim Uddin")).encode())
    assert ei.value.job_id == "j-1"
    assert "Rahim" not in ei.value.reason


def test_result_serialises_to_contract_schema():
    res = JobResult(
        jobId="j-1",
        status=Status.OK,
        items=[
            Item(raw="Tab Napa 500mg 1+0+1 x 5 days", brand="Napa", generic="Paracetamol", atc="N02BE01",
                 strength="500 mg", form="tablet", dose="1 tablet", route="oral", frequency="BD",
                 duration="5 days", conf=FieldConf(drug=0.97, dose=0.95, frequency=0.98, route=0.9, duration=0.95)),
            Item(raw="Xyzzol 10", conf=FieldConf()),  # unknown: all nulls, conf 0
        ],
        timingsMs={"total": 12},
        modelVersion="mediflow-ai/0.1.0",
    )
    d = res.to_json_dict()
    validate_result_dict(d)
    assert set(d) == {"jobId", "status", "items", "timingsMs", "modelVersion"}
    assert set(d["items"][1]) == {"raw", "brand", "generic", "atc", "strength", "form", "dose", "route",
                                  "frequency", "duration", "conf"}
    assert d["items"][1]["generic"] is None and d["items"][1]["conf"]["drug"] == 0.0
    assert json.loads(res.to_bytes()) == d


def test_result_schema_rejects_extra_and_bad_conf():
    bad = {"jobId": "j", "status": "OK", "items": [], "timingsMs": {}, "modelVersion": "m", "patientId": "p"}
    with pytest.raises(jsonschema.ValidationError):
        validate_result_dict(bad)
    item = Item(raw="x").model_dump(by_alias=True)
    item["conf"]["drug"] = 1.5
    with pytest.raises(jsonschema.ValidationError):
        validate_result_dict({"jobId": "j", "status": "OK", "items": [item], "timingsMs": {}, "modelVersion": "m"})


def test_request_schema_direct():
    validate_request_dict(_req())
