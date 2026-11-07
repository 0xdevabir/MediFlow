"""Prometheus metrics: per-stage latency and job counts. No PHI labels."""

from __future__ import annotations

from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest

JOBS = Counter(
    "mediflow_ai_jobs_total",
    "AI prescription jobs completed",
    ["status"],
)
STAGE_DURATION = Histogram(
    "mediflow_ai_stage_duration_seconds",
    "Pipeline stage duration",
    ["stage"],
    buckets=(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2, 4, 8),
)
JOB_DURATION = Histogram(
    "mediflow_ai_job_duration_seconds",
    "End-to-end job duration",
    buckets=(0.05, 0.1, 0.25, 0.5, 1, 2, 4, 8, 10),
)

# Back-compat aliases used in early T09 scaffolding / docs.
STAGE_LATENCY = STAGE_DURATION
JOB_LATENCY = JOB_DURATION


def observe_result(status: str, timings_ms: dict[str, int]) -> None:
    JOBS.labels(status=status).inc()
    total = timings_ms.get("total")
    if total is not None:
        JOB_DURATION.observe(total / 1000.0)
    for stage, ms in timings_ms.items():
        if stage == "total":
            continue
        STAGE_DURATION.labels(stage=stage).observe(ms / 1000.0)


def render() -> tuple[bytes, str]:
    return generate_latest(), CONTENT_TYPE_LATEST
