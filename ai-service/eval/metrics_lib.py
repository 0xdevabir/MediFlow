"""Pure metric helpers used by ``run_eval`` and unit tests."""

from __future__ import annotations

from collections import defaultdict
from typing import Iterable, Sequence


def cer(hyp: str, ref: str) -> float:
    """Character error rate = Levenshtein(hyp, ref) / max(1, len(ref))."""
    if not ref:
        return 0.0 if not hyp else 1.0
    return _lev(hyp, ref) / len(ref)


def _lev(a: str, b: str) -> int:
    if a == b:
        return 0
    if not a:
        return len(b)
    if not b:
        return len(a)
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            ins, delete, sub = cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + (ca != cb)
            cur.append(min(ins, delete, sub))
        prev = cur
    return prev[-1]


def topk_hit(predicted: str | None, truth: str | None, candidates: Sequence[str], k: int = 1) -> bool:
    if not truth:
        return predicted in (None, "")
    pool = []
    if predicted:
        pool.append(predicted)
    for c in candidates:
        if c not in pool:
            pool.append(c)
    truth_n = truth.casefold()
    return any(p.casefold() == truth_n for p in pool[:k])


def field_f1(preds: Iterable[str | None], golds: Iterable[str | None]) -> dict[str, float]:
    tp = fp = fn = 0
    for p, g in zip(preds, golds, strict=False):
        p_n = (p or "").casefold().strip() or None
        g_n = (g or "").casefold().strip() or None
        if p_n and g_n and p_n == g_n:
            tp += 1
        elif p_n and p_n != g_n:
            fp += 1
            if g_n:
                fn += 1
        elif g_n and not p_n:
            fn += 1
    prec = tp / (tp + fp) if (tp + fp) else 0.0
    rec = tp / (tp + fn) if (tp + fn) else 0.0
    f1 = 2 * prec * rec / (prec + rec) if (prec + rec) else 0.0
    return {"precision": prec, "recall": rec, "f1": f1, "tp": tp, "fp": fp, "fn": fn}


def expected_calibration_error(
    confs: Sequence[float],
    correct: Sequence[bool],
    n_bins: int = 10,
) -> tuple[float, list[dict[str, float]]]:
    """ECE over equal-width confidence bins + per-bin reliability stats."""
    assert len(confs) == len(correct)
    bins: list[list[tuple[float, bool]]] = [[] for _ in range(n_bins)]
    for c, ok in zip(confs, correct):
        idx = min(n_bins - 1, max(0, int(c * n_bins)))
        bins[idx].append((c, ok))
    ece = 0.0
    diagram: list[dict[str, float]] = []
    n = len(confs) or 1
    for i, bucket in enumerate(bins):
        lo, hi = i / n_bins, (i + 1) / n_bins
        if not bucket:
            diagram.append({"lo": lo, "hi": hi, "count": 0, "conf": 0.0, "acc": 0.0})
            continue
        conf = sum(c for c, _ in bucket) / len(bucket)
        acc = sum(1 for _, ok in bucket if ok) / len(bucket)
        ece += (len(bucket) / n) * abs(acc - conf)
        diagram.append({"lo": lo, "hi": hi, "count": len(bucket), "conf": conf, "acc": acc})
    return ece, diagram


def reliability_svg(diagram: list[dict[str, float]], size: int = 240) -> str:
    """Tiny dependency-free reliability diagram (SVG)."""
    pad = 30
    inner = size - 2 * pad
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}">',
        f'<rect width="{size}" height="{size}" fill="white"/>',
        f'<line x1="{pad}" y1="{pad}" x2="{size - pad}" y2="{size - pad}" stroke="#ccc" stroke-dasharray="4"/>',
        f'<line x1="{pad}" y1="{size - pad}" x2="{size - pad}" y2="{size - pad}" stroke="#333"/>',
        f'<line x1="{pad}" y1="{pad}" x2="{pad}" y2="{size - pad}" stroke="#333"/>',
        f'<text x="{size / 2}" y="{size - 5}" text-anchor="middle" font-size="10">confidence</text>',
        f'<text x="12" y="{size / 2}" text-anchor="middle" font-size="10" transform="rotate(-90 12 {size / 2})">accuracy</text>',
    ]
    for b in diagram:
        if b["count"] <= 0:
            continue
        x = pad + b["conf"] * inner
        y = size - pad - b["acc"] * inner
        r = 3 + min(8, b["count"] ** 0.5)
        parts.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.1f}" fill="#2563eb" fill-opacity="0.7"/>')
    parts.append("</svg>")
    return "\n".join(parts)
