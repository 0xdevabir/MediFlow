import pytest

from mediflow_ai.extract import parse_duration, parse_frequency


@pytest.mark.parametrize(
    "text, code, per_day",
    [
        ("OD", "OD", 1),
        ("once daily", "OD", 1),
        ("BD", "BD", 2),
        ("b.i.d", "BD", 2),
        ("twice a day", "BD", 2),
        ("TDS", "TDS", 3),
        ("tid", "TDS", 3),
        ("QID", "QID", 4),
        ("HS", "HS", 1),
        ("at bedtime", "HS", 1),
        ("PRN", "PRN", None),
        ("SOS", "PRN", None),
        ("STAT", "STAT", None),
        ("1+0+1", "1+0+1", 2),
        ("1-0-1", "1-0-1", 2),
        ("1+1+1", "1+1+1", 3),
        ("1+0+0+1", "1+0+0+1", 2),
        ("½+0+½", "0.5+0+0.5", 2),
        ("১+০+১", "1+0+1", 2),
        ("every 6 hours", "Q6H", 4),
        ("q8h", "Q8H", 3),
        ("দিনে 3 বার", "TDS", 3),
        ("weekly", "WEEKLY", pytest.approx(1 / 7)),
    ],
)
def test_parse_frequency(text, code, per_day):
    f = parse_frequency(text)
    assert f is not None
    assert f.code == code
    if per_day is None:
        assert f.per_day is None
    else:
        assert f.per_day == per_day


def test_frequency_absent():
    assert parse_frequency("just some words") is None


@pytest.mark.parametrize(
    "text, value",
    [
        ("x 5 days", "5 days"),
        ("for 14 days", "14 days"),
        ("× 7 din", "7 days"),
        ("x ২ weeks", "2 weeks"),
        ("৭ দিন", "7 days"),
        ("continue", "continuous"),
        ("চলবে", "continuous"),
    ],
)
def test_parse_duration(text, value):
    d = parse_duration(text)
    assert d is not None
    assert d[0] == value
