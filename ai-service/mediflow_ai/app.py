"""FastAPI entrypoint: /health, /metrics, optional inline TEXT predict, worker lifespan."""

from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI, Response
from fastapi.responses import PlainTextResponse

from . import __version__
from .config import Settings
from .contract import JobRequest, JobResult, validate_result_dict
from .logging_utils import setup_logging
from .metrics import render
from .pipeline import Pipeline, build_pipeline

log = logging.getLogger("mediflow_ai.app")


def create_app(
    settings: Settings | None = None,
    pipeline: Pipeline | None = None,
    *,
    start_worker: bool | None = None,
) -> FastAPI:
    setup_logging()
    s = settings or Settings.from_env()
    pipe = pipeline or build_pipeline(s)
    worker_flag = s.worker_enabled if start_worker is None else start_worker
    worker_task: asyncio.Task[None] | None = None

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        nonlocal worker_task
        app.state.settings = s
        app.state.pipeline = pipe
        if worker_flag and s.amqp_url:
            from .worker import run_worker

            worker_task = asyncio.create_task(run_worker(s, pipe), name="ai-rx-worker")
            log.info("worker_task_scheduled")
        else:
            log.info("worker_disabled", extra={"hasAmqp": bool(s.amqp_url)})
        try:
            yield
        finally:
            if worker_task is not None:
                worker_task.cancel()
                try:
                    await worker_task
                except asyncio.CancelledError:
                    pass

    app = FastAPI(
        title="MediFlow AI",
        version=__version__,
        description="OCR → NER → DGDA normalisation. Draft only; never writes the clinical record.",
        lifespan=lifespan,
    )

    @app.get("/health")
    def health() -> dict[str, Any]:
        return {
            "status": "ok",
            "version": __version__,
            "ocr": getattr(pipe.ocr, "name", "unknown"),
            "dictionary": {"source": pipe.dictionary.source, "size": pipe.dictionary.size},
            "worker": bool(worker_flag and s.amqp_url),
            "calibrator": "identity" if pipe.calibrator.identity else "isotonic",
        }

    @app.get("/metrics")
    def metrics() -> Response:
        body, ctype = render()
        return Response(content=body, media_type=ctype)

    @app.post("/v1/predict", response_model=None)
    def predict(req: JobRequest) -> dict[str, Any]:
        """Synchronous TEXT/IMAGE predict for local debugging (same contract as the queue)."""
        result: JobResult = pipe.run(req)
        payload = result.to_json_dict()
        validate_result_dict(payload)
        return payload

    @app.get("/", response_class=PlainTextResponse)
    def root() -> str:
        return f"mediflow-ai/{__version__}\n"

    return app


# Uvicorn target: ``uvicorn mediflow_ai.app:app``
app = create_app()

