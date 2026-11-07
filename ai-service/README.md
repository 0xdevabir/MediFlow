# MediFlow AI service (skeleton — T00)

Python 3.11 FastAPI worker for OCR / NER / DGDA normalisation.

**Owned by T09.** This folder is a placeholder so the monorepo layout exists for Wave 1.

## Contract (T06 ↔ T09)

- Request queue: `ai.rx.requests`
- Reply queue: `ai.rx.results`
- DLQ: `ai.rx.dlq`
- Request payload: `{jobId, encounterId, inputType: "IMAGE"|"TEXT", objectKey?, text?, locale}` — **no patient identifiers**
- Result payload: `{jobId, status: "OK"|"UNREADABLE"|"ERROR", items:[...], timingsMs:{...}, modelVersion}`

## Local setup (T09)

```bash
uv python install 3.11
uv venv
uv pip install fastapi uvicorn paddleocr ...
```

Do not put clinical patient identifiers in queue messages. AI never writes to the clinical record.
