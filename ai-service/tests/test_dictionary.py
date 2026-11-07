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
