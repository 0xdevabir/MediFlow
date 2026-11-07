"""Prometheus metrics: per-stage latency and job counts. No PHI labels."""

from __future__ import annotations

from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest

JOBS = Counter(
    "mediflow_ai_jobs_total",
    "AI prescription jobs completed",
    ["status"],
)
STAGE_LATENCY = Histogram(
    "mediflow_ai_stage_latency_seconds",
    "Pipeline stage latency",
    ["stage"],
    buckets=(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2, 4, 8),
)
JOB_LATENCY = Histogram(
    "mediflow_ai_job_latency_seconds",
    "End-to-end job latency",
    buckets=(0.05, 0.1, 0.25, 0.5, 1, 2, 4, 8, 10),
)


def observe_result(status: str, timings_ms: dict[str, int]) -> None:
    JOBS.labels(status=status).inc()
    total = timings_ms.get("total")
    if total is not None:
        JOB_LATENCY.observe(total / 1000.0)
    for stage, ms in timings_ms.items():
        if stage == "total":
            continue
        STAGE_LATENCY.labels(stage=stage).observe(ms / 1000.0)


def render() -> tuple[bytes, str]:
    return generate_latest(), CONTENT_TYPE_LATEST
