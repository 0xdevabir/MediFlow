"""Bangla (Bengali) numeral and punctuation normalisation.

Prescriptions in Bangladesh mix English medicine names with Bangla digits
(e.g. "১+০+১", "৭ দিন"). Everything downstream works on ASCII digits.
"""

from __future__ import annotations

import unicodedata

_BN_DIGITS = "০১২৩৪৫৬৭৮৯"
_TABLE = {ord(bn): str(i) for i, bn in enumerate(_BN_DIGITS)}
# Arabic-Indic / Extended Arabic-Indic digits also turn up in scanned text
_TABLE.update({ord(c): str(i) for i, c in enumerate("٠١٢٣٤٥٦٧٨٩")})
_TABLE.update({ord(c): str(i) for i, c in enumerate("۰۱۲۳۴۵۶۷۸۹")})
# Punctuation that OCR / phone keyboards produce for the "1+0+1" pattern and decimals
_TABLE.update({
    ord("＋"): "+",
    ord("–"): "-",
    ord("—"): "-",
    ord("−"): "-",
    ord("×"): "x",
    ord("।"): ".",  # Bangla danda (sentence end)
})


def bangla_to_ascii_digits(text: str) -> str:
    """Convert Bangla/Arabic-Indic digits to ASCII 0-9; other characters are untouched."""
    return text.translate(_TABLE)


def normalise_text(text: str) -> str:
    """NFC-normalise, convert digits and collapse horizontal whitespace (keeps newlines)."""
    text = unicodedata.normalize("NFC", text)
    text = bangla_to_ascii_digits(text)
    lines = [" ".join(line.split()) for line in text.splitlines()]
    return "\n".join(lines)
