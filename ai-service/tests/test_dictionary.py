from mediflow_ai.dictionary import DrugDictionary


def test_napa_variants_map_to_paracetamol():
    d = DrugDictionary(allow_illustrative=True, cutoff=82.0)
    for q in ("Napa", "NAPA", "Napa 500", "napa"):
        m = d.match(q, strength="500 mg")
        assert m.entry is not None, q
        assert m.entry.generic == "Paracetamol"
        assert m.entry.atc == "N02BE01"
        assert m.score >= 0.82


def test_seclo_and_ciprocin():
    d = DrugDictionary(allow_illustrative=True)
    assert d.match("Seclo").entry.generic == "Omeprazole"
    assert d.match("Ciprocin 500").entry.generic == "Ciprofloxacin"


def test_unknown_returns_null_never_guesses():
    d = DrugDictionary(allow_illustrative=True, cutoff=82.0)
    m = d.match("Xyzzol")
    assert m.entry is None
    assert m.score == 0.0


def test_pharmacy_export_brands_shape(tmp_path):
    """T08/V2-09 export uses {brands, generics, importVersion}, not {entries}."""
    import json

    payload = {
        "importVersion": 3,
        "generics": [
            {
                "id": "11111111-1111-1111-1111-111111111111",
                "name": "Paracetamol",
                "atcCode": "N02BE01",
                "rxnormCode": None,
                "illustrative": False,
            }
        ],
        "brands": [
            {
                "brandId": "22222222-2222-2222-2222-222222222222",
                "brandName": "Napa",
                "strength": "500 mg",
                "form": "tablet",
                "dgdaRegNo": "DGDA-1",
                "genericId": "11111111-1111-1111-1111-111111111111",
                "genericName": "Paracetamol",
                "atcCode": "N02BE01",
                "rxnormCode": None,
                "illustrative": False,
            },
            {
                "brandId": "33333333-3333-3333-3333-333333333333",
                "brandName": "Seclo",
                "strength": "20 mg",
                "form": "capsule",
                "dgdaRegNo": "DGDA-2",
                "genericId": "44444444-4444-4444-4444-444444444444",
                "genericName": "Omeprazole",
                "atc": "A02BC01",
                "rxnorm": None,
                "illustrative": True,
            },
        ],
    }
    path = tmp_path / "export.json"
    path.write_text(json.dumps(payload), encoding="utf-8")
    d = DrugDictionary(file_path=str(path), allow_illustrative=False, cutoff=82.0)
    assert d.size() == 2
    m = d.match("Napa", strength="500 mg")
    assert m.entry is not None
    assert m.entry.generic == "Paracetamol"
    assert m.entry.atc == "N02BE01"
    assert d.match("Seclo").entry.generic == "Omeprazole"
    assert d.match("Seclo").entry.atc == "A02BC01"


def test_empty_pharmacy_export_falls_back_to_illustrative(tmp_path):
    import json

    path = tmp_path / "empty.json"
    path.write_text(
        json.dumps({"importVersion": 0, "generics": [], "brands": []}), encoding="utf-8"
    )
    d = DrugDictionary(file_path=str(path), allow_illustrative=True)
    assert d.source.startswith("illustrative") or d.size() > 0
    assert d.match("Napa").entry is not None


def test_empty_export_without_illustrative_stays_empty(tmp_path):
    import json

    path = tmp_path / "empty.json"
    path.write_text(
        json.dumps({"importVersion": 0, "generics": [], "brands": []}), encoding="utf-8"
    )
    d = DrugDictionary(file_path=str(path), allow_illustrative=False)
    assert d.size() == 0
    assert d.match("Napa").entry is None
