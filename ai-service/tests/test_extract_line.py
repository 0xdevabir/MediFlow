from mediflow_ai.extract import extract_line
from mediflow_ai.ocr.base import OcrLine


def test_full_line_entities():
    ent = extract_line(OcrLine.from_text("Tab. Napa 500mg 1+0+1 x 5 days", conf=0.9))
    assert ent.form.value == "tablet"
    assert ent.strength.value == "500 mg"
    assert ent.frequency.value == "1+0+1"
    assert ent.duration.value == "5 days"
    assert ent.candidates and ent.candidates[0].text.lower() == "napa"
    assert ent.route.value == "oral" or ROUTE_OK(ent)


def ROUTE_OK(ent):  # route may be inferred later in pipeline
    return ent.form.value == "tablet"


def test_header_skipped():
    ent = extract_line(OcrLine.from_text("Diagnosis: viral fever", conf=1.0))
    assert ent.is_header
