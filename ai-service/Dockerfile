# syntax=docker/dockerfile:1.7
# MediFlow AI service (Python 3.11, FastAPI + RabbitMQ worker) - multi-stage, slim, non-root.
# Build context: ai-service/   e.g. docker build -t mediflow-ai ai-service
#
# ASSUMPTIONS (T13, written before T09 landed - adjust ENV defaults, not code):
#   - ai-service/pyproject.toml installs the service package with `pip install .`
#   - optional extras `paddle` / `tesseract` pull the OCR engines (AI_EXTRAS build arg)
#   - FastAPI app import path      : $AI_APP_MODULE    (default app.main:app)
#   - worker entrypoint (python -m): $AI_WORKER_MODULE (default app.worker)
#     T09's WIP package is `mediflow_ai` -> override with
#     AI_APP_MODULE=mediflow_ai.main:app AI_WORKER_MODULE=mediflow_ai.worker
#   - API serves GET /health and GET /metrics on $AI_PORT (8000)
#   - worker serves Prometheus metrics on $MEDIFLOW_AI_METRICS_PORT (9102)
#   - models are read from $MEDIFLOW_AI_MODEL_DIR (/models, volume-mounted) and are
#     never downloaded at runtime (containers sit on an internal-only network).
#
# Commands:  api (default) | worker | any other command (exec'd as-is)

ARG PYTHON_IMAGE=python:3.11.13-slim-bookworm

############################ build ############################
FROM ${PYTHON_IMAGE} AS build
ENV PIP_DISABLE_PIP_VERSION_CHECK=1 PYTHONDONTWRITEBYTECODE=1
RUN apt-get update \
 && apt-get install -y --no-install-recommends build-essential \
 && rm -rf /var/lib/apt/lists/*
RUN python -m venv /opt/venv
ENV PATH=/opt/venv/bin:$PATH
WORKDIR /src
COPY . .
ARG AI_EXTRAS=paddle,tesseract
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --upgrade pip setuptools wheel \
 && if [ -n "$AI_EXTRAS" ]; then pip install ".[${AI_EXTRAS}]"; else pip install .; fi

############################ runtime ############################
FROM ${PYTHON_IMAGE} AS runtime
LABEL org.opencontainers.image.title="mediflow-ai" \
      org.opencontainers.image.source="https://github.com/0xdevabir/MediFlow"

# Shared libs for OpenCV/Paddle; Tesseract binary + eng/ben data for the fallback engine.
ARG INSTALL_TESSERACT=true
RUN apt-get update \
 && apt-get install -y --no-install-recommends libglib2.0-0 libgl1 libgomp1 \
 && if [ "$INSTALL_TESSERACT" = "true" ]; then \
      apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng tesseract-ocr-ben; fi \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --gid 10001 app \
 && useradd --uid 10001 --gid 10001 --create-home --home-dir /home/app --shell /usr/sbin/nologin app \
 && mkdir -p /models && chown app:app /models

COPY --from=build /opt/venv /opt/venv

# Loads Docker secrets (/run/secrets/MEDIFLOW_*) into env vars, then starts api|worker.
COPY --chmod=755 <<'EOF' /usr/local/bin/docker-entrypoint.sh
#!/bin/sh
set -eu
if [ -d /run/secrets ]; then
  for f in /run/secrets/MEDIFLOW_*; do
    [ -f "$f" ] || continue
    name=$(basename "$f")
    # explicit env wins over secret file
    eval "isset=\${$name+x}"
    if [ -z "$isset" ]; then export "$name=$(cat "$f")"; fi
  done
fi
case "${1:-api}" in
  api)    exec uvicorn "$AI_APP_MODULE" --host 0.0.0.0 --port "$AI_PORT" --no-server-header --no-access-log ;;
  worker) exec python -m "$AI_WORKER_MODULE" ;;
  *)      exec "$@" ;;
esac
EOF

ENV PATH=/opt/venv/bin:$PATH \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    HOME=/home/app \
    TZ=UTC \
    AI_APP_MODULE=mediflow_ai.app:app \
    AI_WORKER_MODULE=mediflow_ai.worker \
    AI_PORT=8000 \
    MEDIFLOW_AI_METRICS_PORT=9102 \
    MEDIFLOW_AI_MODEL_DIR=/models

# Optional: bake models into the image at build time (otherwise mount /models).
ARG PRELOAD_MODELS_CMD=""
RUN if [ -n "$PRELOAD_MODELS_CMD" ]; then su app -s /bin/sh -c "$PRELOAD_MODELS_CMD"; fi

USER 10001:10001
WORKDIR /home/app
EXPOSE 8000 9102

# API healthcheck; the worker service overrides this in compose (metrics port).
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD ["python", "-c", "import os,sys,urllib.request; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:%s/health' % os.environ.get('AI_PORT','8000'), timeout=4).status == 200 else 1)"]

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["api"]
