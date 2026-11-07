# T09 — AI service: OCR, entity extraction, DGDA normalisation, confidence (Python)

Read PLAN + CONVENTIONS (**AI service contract** — follow exactly). Research: PDF §11 Figure 7 + text, evaluation table; DOCX §10.1 Table 12, §10.2, §10.5, §10.6 Table 15, §11.1 "AI-specific".

## Owns
`ai-service/**` only.

## Build
1. Python 3.11, FastAPI app + worker (aio-pika) consuming `ai.rx.requests`, publishing `ai.rx.results`; DLQ on poison messages; per-job timeout 8 s (Java gives up at 10 s).
2. Pipeline (photos go through all stages; typed text starts at stage 3):
   1. Pre-processing (OpenCV): deskew, denoise, contrast, binarise.
   2. OCR: PaddleOCR (en) with per-token confidence; Tesseract fallback; Bangla numerals → ASCII digits.
   3. Entity extraction: rule-based + spaCy pipeline for drug, strength, route, frequency (OD, BD, TDS, QID, HS, 1+0+1 style), duration; per-field confidence.
   4. Normalisation: RapidFuzz match against dictionary exported from backend (`/api/internal/dictionary/export`, cached, refreshed hourly) brand → generic → ATC; drug confidence = OCR conf × match score; **no match → generic null, conf 0** (never guess).
   5. Calibration hook: isotonic calibrator loaded from file if present (identity otherwise).
3. `UNREADABLE` status if image quality / OCR yield is too low.
4. **Privacy**: no network calls except RabbitMQ, MinIO, backend; no third-party AI APIs; logs without PHI (only jobId, timings, model version).
5. `GET /health`, `/metrics` (prometheus_client: latency per stage, job counts).
6. Dockerfile (slim, models baked or volume-mounted); ONNX export path documented for later.
7. **Evaluation harness** `ai-service/eval/`: scripts computing CER, top-1/top-3 drug match, field F1, ECE + reliability diagram from a labelled folder; synthetic test set generator (render typed prescriptions with handwriting-like fonts + noise) so metrics run in CI.

## Acceptance
- pytest: frequency parser cases, Bangla numeral conversion, brand matching (Napa/NAPA/Napa 500 → Paracetamol), unknown → null, contract JSON schema validation, end-to-end on synthetic images, timeout behaviour.
