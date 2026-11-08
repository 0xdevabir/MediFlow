# MediFlow AI service (T09)

Python 3.11 FastAPI worker: prescription photo/text → OCR → entity extraction → DGDA brand→generic→ATC → per-field confidence.

**Draft only.** The AI never signs, dispenses, or writes the clinical record. Queue payloads carry **no patient identifiers**.

## Contract (T06 ↔ T09)

| Queue | Purpose |
|-------|---------|
| `ai.rx.requests` | Job request |
| `ai.rx.results` | Job result |
| `ai.rx.dlq` | Poison messages (bad JSON / schema) |

- Request: `{jobId, encounterId, inputType: "IMAGE"|"TEXT", objectKey?, text?, locale}`
- Result: `{jobId, status: "OK"|"UNREADABLE"|"ERROR", items:[...], timingsMs:{...}, modelVersion}`
- Item confidence fields: `drug, dose, frequency, route, duration`. Java (T06) takes `min(drug,dose,frequency)`, runs the contradiction engine, and applies the 0.85 gate.
- Unknown brand → `generic`/`atc` null, `conf.drug = 0` (never guess).
- Per-job timeout **8 s** (Java UI gives up at 10 s).

JSON Schemas: `mediflow_ai/schemas/`.

## Pipeline

1. **Pre-process** (IMAGE): OpenCV deskew / denoise / CLAHE / Otsu.
2. **OCR**: PaddleOCR (en) if installed, else Tesseract; Bangla numerals → ASCII.
3. **Extract**: rule-based NER (drug candidate, strength, dose, route, frequency incl. `1+0+1` / OD–QID / HS, duration).
4. **Normalise**: RapidFuzz against `/api/internal/dictionary/export` (`{brands,generics,importVersion}` or legacy `{entries}`; cached, hourly refresh). No match → null / conf 0.
5. **Calibrate**: isotonic map from `models/calibrator.json` if present; else identity.

`UNREADABLE` when image quality / OCR yield is too low.

## Local setup

```bash
cd ai-service
uv python install 3.11
uv venv --python 3.11
source .venv/bin/activate
uv pip install -e '.[dev]'
# optional OCR engines:
# uv pip install -e '.[tesseract]'   # also: brew install tesseract
# uv pip install -e '.[paddle]'

export MEDIFLOW_AI_OCR_BACKEND=fake          # or auto|paddle|tesseract|none
export MEDIFLOW_AI_WORKER_ENABLED=false      # API-only
uvicorn mediflow_ai.app:app --port 8090
# curl localhost:8090/health
# curl -X POST localhost:8090/v1/predict -H 'content-type: application/json' \
#   -d '{"jobId":"j1","encounterId":"e1","inputType":"TEXT","text":"Tab Napa 500mg 1+0+1 x 5 days","locale":"en"}'
```

### Worker + MinIO + dictionary

```bash
export MEDIFLOW_AI_AMQP_URL=amqp://mediflow:mediflow@localhost:5672/
export MEDIFLOW_AI_S3_ENDPOINT=localhost:9000
export MEDIFLOW_AI_S3_ACCESS_KEY=mediflow
export MEDIFLOW_AI_S3_SECRET_KEY=mediflowsecret
export MEDIFLOW_AI_S3_BUCKET=prescriptions
export MEDIFLOW_AI_DICTIONARY_URL=http://localhost:8080/api/internal/dictionary/export
export MEDIFLOW_AI_DICTIONARY_TOKEN=...          # same value as MEDIFLOW_PHARMACY_INTERNAL_API_KEY
export MEDIFLOW_AI_DICTIONARY_TOKEN_HEADER=X-Mediflow-Internal-Key  # default; matches pharmacy filter
export MEDIFLOW_AI_WORKER_ENABLED=true
uvicorn mediflow_ai.app:app --port 8090
```

Privacy: the process may open connections only to RabbitMQ, MinIO, and the backend dictionary URL. No third-party AI APIs. Logs carry `jobId`, timings, model version — never free-text / PHI.

## Docker

```bash
docker build -t mediflow-ai ./ai-service
docker run --rm -p 8090:8090 \
  -e MEDIFLOW_AI_AMQP_URL=amqp://... \
  -e MEDIFLOW_AI_OCR_BACKEND=tesseract \
  -v "$PWD/models:/models" \
  mediflow-ai
```

Tesseract is in the base image. PaddleOCR is optional (large); uncomment the paddle layer in the Dockerfile or mount weights.

## ONNX / model baking (later)

Path for a future optimisation (not required for v1):

1. Export the Paddle det/rec models (or a distilled recogniser) to ONNX.
2. Place `*.onnx` under `/models` (volume or image layer). Do **not** commit weights.
3. Add an `onnx` OCR backend selected via `MEDIFLOW_AI_OCR_BACKEND=onnx`.
4. Keep the same `OcrResult` contract so the rest of the pipeline is unchanged.

The calibrator file format is already ONNX-adjacent tooling-friendly:

```json
{"x": [0.0, 0.5, 0.8, 1.0], "y": [0.0, 0.4, 0.75, 0.98]}
```

## Evaluation harness

```bash
# Synthetic set (Pillow) + metrics — no real OCR needed when using --backend fake
python -m eval.generate_synthetic --out eval/synthetic --n 30
python -m eval.run_eval --data eval/synthetic --backend fake --out eval/out
```

Reports CER, top-1/top-3 drug match, field F1, ECE + a reliability diagram PNG.

## Tests

```bash
uv pip install -e '.[dev]'
pytest            # do not run in agent sessions that forbid it; CI will
```
