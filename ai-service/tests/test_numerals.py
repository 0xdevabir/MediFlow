from mediflow_ai.numerals import bangla_to_ascii_digits, normalise_text


def test_bangla_digits():
    assert bangla_to_ascii_digits("১+০+১") == "1+0+1"
    assert bangla_to_ascii_digits("৭ দিন") == "7 দিন"
    assert bangla_to_ascii_digits("৫০০") == "500"


def test_arabic_indic_digits():
    assert bangla_to_ascii_digits("١٢٣") == "123"


def test_normalise_collapses_space_keeps_newlines():
    assert normalise_text("  Napa   500  \n  BD  ") == "Napa 500\nBD"


def test_plus_and_dash_variants():
    assert bangla_to_ascii_digits("1＋0＋1") == "1+0+1"
    assert bangla_to_ascii_digits("1–0—1") == "1-0-1"
