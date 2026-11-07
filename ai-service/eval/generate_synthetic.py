"""Render typed prescriptions with handwriting-like fonts + noise for CI metrics."""

from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

SAMPLES = [
    {
        "text": "Tab. Napa 500mg 1+0+1 x 5 days",
        "brand": "Napa",
        "generic": "Paracetamol",
        "frequency": "1+0+1",
        "duration": "5 days",
        "strength": "500 mg",
    },
    {
        "text": "Cap Seclo 20mg BD x 14 days",
        "brand": "Seclo",
        "generic": "Omeprazole",
        "frequency": "BD",
        "duration": "14 days",
        "strength": "20 mg",
    },
    {
        "text": "Tab Ciprocin 500 mg TDS for 7 days",
        "brand": "Ciprocin",
        "generic": "Ciprofloxacin",
        "frequency": "TDS",
        "duration": "7 days",
        "strength": "500 mg",
    },
    {
        "text": "Syp Ace 2 tsf TDS x 3 days",
        "brand": "Ace",
        "generic": "Paracetamol",
        "frequency": "TDS",
        "duration": "3 days",
        "dose": "2 teaspoons",
    },
    {
        "text": "Tab Monas 10mg HS x 30 days",
        "brand": "Monas",
        "generic": "Montelukast",
        "frequency": "HS",
        "duration": "30 days",
        "strength": "10 mg",
    },
    {
        "text": "Tab Amdocal 5 mg OD",
        "brand": "Amdocal",
        "generic": "Amlodipine",
        "frequency": "OD",
        "strength": "5 mg",
    },
    {
        "text": "১+০+১ Napa ৫০০mg x ৭ দিন",
        "brand": "Napa",
        "generic": "Paracetamol",
        "frequency": "1+0+1",
        "duration": "7 days",
    },
    {
        "text": "Tab Xyzzol 10mg OD x 5 days",
        "brand": None,
        "generic": None,
        "frequency": "OD",
        "duration": "5 days",
        "unknown": True,
    },
]


def _font(size: int) -> ImageFont.ImageFont:
    for name in (
        "/System/Library/Fonts/Supplemental/Comic Sans MS.ttf",
        "/System/Library/Fonts/Supplemental/Chalkboard.ttc",
        "/Library/Fonts/Arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ):
        p = Path(name)
        if p.is_file():
            try:
                return ImageFont.truetype(str(p), size=size)
            except OSError:
                continue
    return ImageFont.load_default()


def render_line(text: str, rng: random.Random) -> Image.Image:
    font = _font(rng.randint(22, 34))
    # Measure
    probe = Image.new("L", (10, 10), 255)
    draw = ImageDraw.Draw(probe)
    bbox = draw.textbbox((0, 0), text, font=font)
    w, h = bbox[2] - bbox[0] + 40, bbox[3] - bbox[1] + 40
    img = Image.new("RGB", (max(w, 200), max(h, 60)), (255, 255, 255))
    draw = ImageDraw.Draw(img)
    x = 12 + rng.randint(0, 8)
    y = 10 + rng.randint(0, 8)
    # Slight per-character jitter to mimic handwriting
    cx = x
    for ch in text:
        oy = rng.randint(-2, 2)
        draw.text((cx, y + oy), ch, fill=(20, 20, 20), font=font)
        bb = draw.textbbox((0, 0), ch, font=font)
        cx += (bb[2] - bb[0]) + rng.randint(0, 1)
    if rng.random() < 0.7:
        img = img.filter(ImageFilter.GaussianBlur(radius=rng.uniform(0.2, 0.9)))
    # Salt-and-pepper
    px = img.load()
    n_noise = int(img.size[0] * img.size[1] * rng.uniform(0.001, 0.01))
    for _ in range(n_noise):
        i, j = rng.randint(0, img.size[0] - 1), rng.randint(0, img.size[1] - 1)
        v = 0 if rng.random() < 0.5 else 255
        px[i, j] = (v, v, v)
    return img


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", type=Path, default=Path("eval/synthetic"))
    ap.add_argument("--n", type=int, default=24)
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args(argv)
    args.out.mkdir(parents=True, exist_ok=True)
    rng = random.Random(args.seed)
    labels = []
    for i in range(args.n):
        sample = SAMPLES[i % len(SAMPLES)].copy()
        img = render_line(sample["text"], rng)
        name = f"rx_{i:03d}.png"
        img.save(args.out / name)
        # Sidecar that FakeOcr can be keyed on in eval (we also store expected text)
        labels.append({"image": name, **sample})
    (args.out / "labels.json").write_text(json.dumps(labels, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"wrote {args.n} images + labels.json → {args.out}")


if __name__ == "__main__":
    main()
