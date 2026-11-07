"""Stage 3: rule-based entity extraction (drug candidate, strength, dose, route, frequency, duration).

Each field gets ``certainty x OCR confidence`` of the characters it was read from.
Patterns cover common Bangladeshi prescribing style: ``Tab. Napa 500mg 1+0+1 x 5 days``,
``Cap Seclo 20 BD`` , ``Syp Ace 2 tsf TDS``, Bangla digits/words (``১+০+১``, ``৭ দিন``).

Output vocabulary
- frequency: an abbreviation code (``OD BD TDS QID HS PRN STAT WEEKLY Q<n>H``) or the
  Bangladeshi slot pattern itself (``1+0+1`` = morning+noon+night, optional 4th slot).
- duration: ``"<n> day(s)|week(s)|month(s)"`` or ``"continuous"``.
- route: ``oral intravenous intramuscular subcutaneous topical inhalation rectal sublingual nasal ophthalmic otic``.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

from .numerals import normalise_text
from .ocr.base import OcrLine

MASK = "\x00"


@dataclass(frozen=True)
class Frequency:
    code: str
    per_day: float | None
    certainty: float
    slots: tuple[float, ...] | None = None  # set for 1+0+1 style patterns


@dataclass
class Field:
    value: str | None = None
    conf: float = 0.0


@dataclass
class Candidate:
    text: str
    conf: float  # OCR confidence of the characters


@dataclass
class LineEntities:
    raw: str
    form: Field = field(default_factory=Field)
    strength: Field = field(default_factory=Field)
    dose: Field = field(default_factory=Field)
    route: Field = field(default_factory=Field)
    frequency: Field = field(default_factory=Field)
    duration: Field = field(default_factory=Field)
    slot_qty: float | None = None  # uniform per-slot quantity from a 1+0+1 pattern
    slot_conf: float = 0.0
    strength_number: str | None = None  # bare number next to the name ("Napa 500")
    candidates: list[Candidate] = field(default_factory=list)  # drug-name n-grams, longest first
    words: list[str] = field(default_factory=list)
    is_header: bool = False

    @property
    def has_med_signal(self) -> bool:
        return any(f.value for f in (self.form, self.strength, self.dose, self.frequency, self.duration))


# ---------------------------------------------------------------- frequency

_SLOT = r"(½|\d(?:\.5)?)"
_PATTERN_RE = re.compile(
    rf"(?<![\d.]){_SLOT}\s*[+\-]\s*{_SLOT}\s*[+\-]\s*{_SLOT}(?:\s*[+\-]\s*{_SLOT})?(?![\d.])"
)
_ABBR = {
    "od": "OD", "qd": "OD", "bd": "BD", "bid": "BD", "tds": "TDS", "tid": "TDS",
    "qid": "QID", "qds": "QID", "hs": "HS", "sos": "PRN", "prn": "PRN", "stat": "STAT",
}
_PER_DAY = {"OD": 1, "BD": 2, "TDS": 3, "QID": 4, "HS": 1, "PRN": None, "STAT": None, "WEEKLY": 1 / 7}
_ABBR_RE = re.compile(
    r"(?<![a-z0-9])(o\.?d|q\.?d|b\.?i\.?d|b\.?d|t\.?d\.?s|t\.?i\.?d|q\.?i\.?d|q\.?d\.?s|h\.?s|s\.?o\.?s|p\.?r\.?n|stat)\.?(?![a-z])"
)
_PHRASES: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"\b(once\s+(a\s+)?da(il)?y|once\s+daily|(1|one)\s*times?\s*(a\s+)?da(il)?y|daily)\b"), "OD"),
    (re.compile(r"\b(twice\s+(a\s+)?da(il)?y|twice|(2|two)\s*times?\s*(a\s+)?da(il)?y)\b"), "BD"),
    (re.compile(r"\b(thrice(\s+(a\s+)?da(il)?y)?|(3|three)\s*times?(\s*(a\s+)?da(il)?y)?)\b"), "TDS"),
    (re.compile(r"\b((4|four)\s*times?(\s*(a\s+)?da(il)?y)?)\b"), "QID"),
    (re.compile(r"\b(at\s+night|at\s+bed\s*time|bed\s*time|before\s+sleep)\b"), "HS"),
    (re.compile(r"\b(when\s+needed|as\s+needed|if\s+needed|when\s+required)\b"), "PRN"),
    (re.compile(r"\b(once\s+(a\s+)?week|weekly)\b"), "WEEKLY"),
    (re.compile(r"রাতে"), "HS"),
]
_HOURLY_RE = re.compile(r"\b(?:every\s+(\d{1,2})\s*(?:h|hr|hrs|hours?)|(\d{1,2})\s*(?:-\s*)?hourly|q\s*(\d{1,2})\s*h)\b")
_BN_TIMES_RE = re.compile(r"দিনে\s*(\d)\s*বার")


def _slot_value(s: str) -> float:
    return 0.5 if s == "½" else float(s)


def format_num(v: float) -> str:
    return str(int(v)) if float(v).is_integer() else f"{v:g}"


_fmt_num = format_num  # internal alias


def _freq_from_pattern(m: re.Match[str]) -> Frequency | None:
    slots = tuple(_slot_value(g) for g in m.groups() if g is not None)
    nonzero = [s for s in slots if s > 0]
    if not nonzero:
        return None
    return Frequency("+".join(_fmt_num(s) for s in slots), float(len(nonzero)), 0.98, slots)


def parse_frequency(text: str) -> Frequency | None:
    """Parse the first frequency expression in ``text`` (case-insensitive, Bangla digits ok)."""
    found = _find_frequency(normalise_text(text).lower())
    return found[0] if found else None


def _find_frequency(low: str) -> tuple[Frequency, int, int] | None:
    m = _PATTERN_RE.search(low)
    if m:
        f = _freq_from_pattern(m)
        if f:
            return f, m.start(), m.end()
    m = _HOURLY_RE.search(low)
    if m:
        n = int(next(g for g in m.groups() if g))
        if 1 <= n <= 24:
            return Frequency(f"Q{n}H", 24 / n, 0.95), m.start(), m.end()
    m = _ABBR_RE.search(low)
    if m:
        key = re.sub(r"[^a-z]", "", m.group(1))
        code = _ABBR[key]
        return Frequency(code, _PER_DAY[code], 0.97), m.start(), m.end()
    m = _BN_TIMES_RE.search(low)
    if m:
        n = int(m.group(1))
        code = {1: "OD", 2: "BD", 3: "TDS", 4: "QID"}.get(n)
        if code:
            return Frequency(code, float(n), 0.9), m.start(), m.end()
    for rx, code in _PHRASES:
        m = rx.search(low)
        if m:
            return Frequency(code, _PER_DAY[code], 0.93), m.start(), m.end()
    return None


# ---------------------------------------------------------------- duration

_DUR_RE = re.compile(
    r"(?:(?<![a-z])x\s*|×\s*|\bfor\s+)?(\d{1,3})\s*(days?|d|din|দিন|weeks?|wks?|wk|w|সপ্তাহ|months?|mths?|mo|মাস)(?![a-z])"
)
_CONT_RE = re.compile(r"\b(continue|cont\.?|continuous(ly)?|ongoing|long\s*term)(?![a-z])|চলবে")


def _dur_unit(u: str) -> str:
    if u.startswith(("d", "দ")):
        return "day"
    if u.startswith(("w", "স")):
        return "week"
    return "month"


def parse_duration(text: str) -> tuple[str, float, int, int] | None:
    low = normalise_text(text).lower()
    m = _DUR_RE.search(low)
    if m:
        n = int(m.group(1))
        if n == 0:
            return None
        unit = _dur_unit(m.group(2))
        prefixed = m.group(0)[0] in "x×f"
        return f"{n} {unit}{'s' if n != 1 else ''}", 0.97 if prefixed else 0.93, m.start(), m.end()
    m = _CONT_RE.search(low)
    if m:
        return "continuous", 0.9, m.start(), m.end()
    return None


# ---------------------------------------------------------------- strength / dose / route / form

_UNIT = r"(mg|mcg|µg|ug|gm|g|iu|ml|%)"
_STRENGTH_RE = re.compile(
    rf"(?<![\d.])(\d+(?:\.\d+)?)(?:\s*/\s*(\d+(?:\.\d+)?))?\s*{_UNIT}(?:\s*/\s*(\d+(?:\.\d+)?)?\s*(ml|g))?(?![a-z])"
)
_UNIT_NORM = {"gm": "g", "ug": "mcg", "µg": "mcg", "iu": "IU"}

_DOSE_RE = re.compile(
    r"(?<![\d.])(\d+(?:\.\d+)?|½|1/2)\s*(tabs?|tablets?|caps?|capsules?|tsf|tsp|teaspoons?|spoons?|ml|puffs?|drops?|"
    r"amps?|ampoules?|vials?|sachets?|units?|patch(?:es)?)(?![a-z])"
)
_DOSE_UNIT = {
    "tab": "tablet", "tabs": "tablet", "tablet": "tablet", "tablets": "tablet",
    "cap": "capsule", "caps": "capsule", "capsule": "capsule", "capsules": "capsule",
    "tsf": "teaspoon", "tsp": "teaspoon", "teaspoon": "teaspoon", "teaspoons": "teaspoon",
    "spoon": "teaspoon", "spoons": "teaspoon", "ml": "ml", "puff": "puff", "puffs": "puff",
    "drop": "drop", "drops": "drop", "amp": "ampoule", "amps": "ampoule", "ampoule": "ampoule",
    "ampoules": "ampoule", "vial": "vial", "vials": "vial", "sachet": "sachet", "sachets": "sachet",
    "unit": "unit", "units": "unit", "patch": "patch", "patches": "patch",
}

_ROUTE_RE = re.compile(
    r"(?<![a-z])(p\.?o|per\s+oral|oral(?:ly)?|by\s+mouth|i\.?v|i\.?m|s\.?c|subcut\w*|topical(?:ly)?|local(?:ly)?|"
    r"inhal\w*|p\.?r|rectal(?:ly)?|s\.?l|sublingual(?:ly)?|nasal(?:ly)?|in\s+(?:the\s+)?eyes?|in\s+(?:the\s+)?ears?)\.?(?![a-z])"
)


def _route_norm(s: str) -> str:
    k = re.sub(r"[^a-z]", "", s)
    if k in {"po", "peroral", "oral", "orally", "bymouth"}:
        return "oral"
    if k == "iv":
        return "intravenous"
    if k == "im":
        return "intramuscular"
    if k == "sc" or k.startswith("subcut"):
        return "subcutaneous"
    if k.startswith(("topical", "local")):
        return "topical"
    if k.startswith("inhal"):
        return "inhalation"
    if k in {"pr", "rectal", "rectally"}:
        return "rectal"
    if k in {"sl"} or k.startswith("sublingual"):
        return "sublingual"
    if k.startswith("nasal"):
        return "nasal"
    if "eye" in k:
        return "ophthalmic"
    return "otic"


_FORM_RE = re.compile(
    r"(?<![a-z])(tabs?|tablets?|caps?|capsules?|syp|syr|syrup|susp|suspension|inj|injection|drops?|gtt|cream|crm|"
    r"oint|ointment|gel|inh|inhaler|supp|suppository|sachet|lotion|spray|soln|solution)\.?(?![a-z])"
)
_FORM_NORM = {
    "tab": "tablet", "tabs": "tablet", "tablet": "tablet", "tablets": "tablet",
    "cap": "capsule", "caps": "capsule", "capsule": "capsule", "capsules": "capsule",
    "syp": "syrup", "syr": "syrup", "syrup": "syrup", "susp": "suspension", "suspension": "suspension",
    "inj": "injection", "injection": "injection", "drop": "drops", "drops": "drops", "gtt": "drops",
    "cream": "cream", "crm": "cream", "oint": "ointment", "ointment": "ointment", "gel": "gel",
    "inh": "inhaler", "inhaler": "inhaler", "supp": "suppository", "suppository": "suppository",
    "sachet": "sachet", "lotion": "lotion", "spray": "spray", "soln": "solution", "solution": "solution",
}
ROUTE_FROM_FORM = {
    "tablet": "oral", "capsule": "oral", "syrup": "oral", "suspension": "oral", "sachet": "oral",
    "cream": "topical", "ointment": "topical", "gel": "topical", "lotion": "topical",
    "inhaler": "inhalation", "suppository": "rectal",
}
DOSE_UNIT_FROM_FORM = {"tablet": "tablet", "capsule": "capsule", "suppository": "suppository", "sachet": "sachet"}

_HEADER_RE = re.compile(
    r"^\W*(dx|diagnosis|c/c|cc|o/e|adv|advice|inv|investigations?|name|age|sex|date|dr|doctor|reg|bmdc|"
    r"follow|next\s+visit|signature|patient|mobile|phone|address|bp|pulse|temp|weight)\b"
)
_RX_PREFIX_RE = re.compile(r"^\W*(?:rx|r/x|℞)\b\W*")
_NUMBERING_RE = re.compile(r"^\s*\d{1,2}\s*[.)]\s*")
_WORD_RE = re.compile(r"[a-z][a-z0-9\-]*|\d+(?:\.\d+)?")
STOPWORDS = frozenset(
    "and with after before meal meals food empty stomach for then each one plus take daily days day "
    "morning noon night evening ac pc bm am pm khabar pore age por the a an of to in on at".split()
)


def _mask(chars: list[str], start: int, end: int) -> None:
    for i in range(start, end):
        chars[i] = MASK


def _span_conf(char_conf: list[float], start: int, end: int) -> float:
    seg = char_conf[start:end]
    return min(seg) if seg else 0.0


def extract_line(line: OcrLine) -> LineEntities:
    """Extract entities from one OCR/text line."""
    raw = line.text
    char_conf = line.char_confidences()
    low = raw.lower()
    # normalise_text never changes string length for ASCII/Bangla digits, but guard anyway
    norm = normalise_text(low)
    if len(norm) == len(low):
        low = norm
    ent = LineEntities(raw=raw)

    m = _RX_PREFIX_RE.match(low)
    work = list(low)
    if m:
        _mask(work, 0, m.end())
    elif _HEADER_RE.match(low):
        ent.is_header = True
        return ent
    m = _NUMBERING_RE.match(low)
    if m:
        _mask(work, m.start(), m.end())

    def text() -> str:
        return "".join(work)

    def take(rx: re.Pattern[str]) -> re.Match[str] | None:
        found = rx.search(text())
        if found:
            _mask(work, found.start(), found.end())
        return found

    # frequency first (1+0+1 also implies per-slot dose)
    found = _find_frequency(text())
    if found:
        freq, s, e = found
        c = freq.certainty * _span_conf(char_conf, s, e)
        ent.frequency = Field(freq.code, c)
        if freq.slots:
            nonzero = {v for v in freq.slots if v > 0}
            if len(nonzero) == 1:
                ent.slot_qty, ent.slot_conf = nonzero.pop(), c
        _mask(work, s, e)

    dur = parse_duration(text())
    if dur:
        value, cert, s, e = dur
        ent.duration = Field(value, cert * _span_conf(char_conf, s, e))
        _mask(work, s, e)

    # strengths; a pure "N ml" is a dose (syrups), never the strength
    while True:
        m = _STRENGTH_RE.search(text())
        if not m:
            break
        _mask(work, m.start(), m.end())
        num, combo, unit, per_num, per_unit = m.groups()
        unit_n = _UNIT_NORM.get(unit, unit)
        conf = 0.97 * _span_conf(char_conf, m.start(), m.end())
        if unit == "ml" and not combo and not per_unit:
            if not ent.dose.value:
                ent.dose = Field(f"{num} ml", 0.93 * _span_conf(char_conf, m.start(), m.end()))
            continue
        if ent.strength.value:
            continue
        value = f"{num}/{combo} {unit_n}" if combo else f"{num} {unit_n}"
        if per_unit:
            value += f"/{per_num + ' ' if per_num else ''}{per_unit}"
        ent.strength = Field(value, conf)

    m = take(_DOSE_RE)
    if m and not ent.dose.value:
        qty = 0.5 if m.group(1) in {"½", "1/2"} else float(m.group(1))
        unit = _DOSE_UNIT[m.group(2)]
        plural = "s" if qty > 1 and unit != "ml" else ""
        ent.dose = Field(f"{_fmt_num(qty)} {unit}{plural}", 0.95 * _span_conf(char_conf, m.start(), m.end()))

    m = take(_ROUTE_RE)
    if m:
        ent.route = Field(_route_norm(m.group(1)), 0.95 * _span_conf(char_conf, m.start(), m.end()))

    m = take(_FORM_RE)
    if m:
        ent.form = Field(_FORM_NORM[m.group(1)], 0.95 * _span_conf(char_conf, m.start(), m.end()))

    # leftovers: drug-name words (contiguous runs) and a bare number ("Napa 500")
    rest = text()
    runs: list[list[tuple[str, int, int]]] = [[]]
    pos = 0
    for wm in _WORD_RE.finditer(rest):
        gap = rest[pos:wm.start()]
        pos = wm.end()
        w = wm.group(0)
        if MASK in gap and runs[-1]:
            runs.append([])
        if w[0].isdigit():
            if ent.strength_number is None and not ent.strength.value and runs[-1]:
                ent.strength_number = w
            if runs[-1]:
                runs.append([])
            continue
        if w in STOPWORDS or len(w) < 2:
            if runs[-1]:
                runs.append([])
            continue
        runs[-1].append((w, wm.start(), wm.end()))
        ent.words.append(w)

    cands: list[Candidate] = []
    for run in runs:
        for n in (3, 2, 1):
            for i in range(0, len(run) - n + 1):
                seg = run[i:i + n]
                s, e = seg[0][1], seg[-1][2]
                cands.append(Candidate(" ".join(w for w, _, _ in seg), _span_conf(char_conf, s, e)))
    cands.sort(key=lambda c: -len(c.text.split()))
    ent.candidates = cands
    return ent
