# MediFlow AI service — slim image. OCR model weights are volume-mounted or
# baked in a later build stage (see README "ONNX / model baking").
FROM python:3.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    MEDIFLOW_AI_OCR_BACKEND=auto \
    MEDIFLOW_AI_CALIBRATOR_PATH=/models/calibrator.json

RUN apt-get update && apt-get install -y --no-install-recommends \
        libgl1 libglib2.0-0 libgomp1 tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pyproject.toml README.md ./
COPY mediflow_ai ./mediflow_ai

RUN pip install --no-cache-dir . \
    && pip install --no-cache-dir pytesseract==0.3.13 \
    && useradd --create-home --uid 10001 mediflow

# Optional PaddleOCR layer (large). Uncomment to bake engines into the image:
# RUN pip install --no-cache-dir '.[paddle]'

VOLUME ["/models"]
USER mediflow
EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/health')" || exit 1

CMD ["uvicorn", "mediflow_ai.app:app", "--host", "0.0.0.0", "--port", "8090"]
