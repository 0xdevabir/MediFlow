"""aio-pika worker: consume ai.rx.requests, publish ai.rx.results, poison → DLQ.

Per-job timeout defaults to 8 s (Java UI gives up at 10 s).
"""

from __future__ import annotations

import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor
from typing import Any

import aio_pika
from aio_pika import ExchangeType, IncomingMessage, Message, DeliveryMode

from .config import Settings
from .contract import DLQ, REQUEST_QUEUE, RESULT_QUEUE, JobResult, PoisonMessage, Status, parse_request
from .metrics import observe_result
from prometheus_client import start_http_server
import os
from .pipeline import Pipeline, MODEL_VERSION

log = logging.getLogger("mediflow_ai.worker")

def _maybe_start_metrics() -> None:
    port = int(os.environ.get("MEDIFLOW_AI_METRICS_PORT", "9102"))
    if port > 0:
        start_http_server(port)
        log.info("AI worker metrics on :%s", port)


async def run_worker(settings: Settings, pipeline: Pipeline) -> None:
    if not settings.amqp_url:
        raise RuntimeError("MEDIFLOW_AI_AMQP_URL is required for the worker")

    executor = ThreadPoolExecutor(max_workers=settings.worker_threads, thread_name_prefix="ai-job")
    connection = await aio_pika.connect_robust(settings.amqp_url)
    channel = await connection.channel()
    await channel.set_qos(prefetch_count=settings.prefetch)

    # Declare durable queues + a fanout-ish direct topology the Java side can share.
    exchange = await channel.declare_exchange("ai.rx", ExchangeType.DIRECT, durable=True)
    dlq = await channel.declare_queue(DLQ, durable=True)
    await dlq.bind(exchange, routing_key=DLQ)

    results = await channel.declare_queue(RESULT_QUEUE, durable=True)
    await results.bind(exchange, routing_key=RESULT_QUEUE)

    args: dict[str, Any] = {"x-dead-letter-exchange": "ai.rx", "x-dead-letter-routing-key": DLQ}
    requests = await channel.declare_queue(REQUEST_QUEUE, durable=True, arguments=args)
    await requests.bind(exchange, routing_key=REQUEST_QUEUE)

    log.info("worker_started", extra={"prefetch": settings.prefetch, "timeoutS": settings.job_timeout_s})

    async with requests.iterator() as queue_iter:
        async for message in queue_iter:
            await _handle(message, exchange, pipeline, executor, settings.job_timeout_s)


async def _handle(
    message: IncomingMessage,
    exchange: aio_pika.Exchange,
    pipeline: Pipeline,
    executor: ThreadPoolExecutor,
    timeout_s: float,
) -> None:
    async with message.process(requeue=False, ignore_processed=True):
        try:
            req = parse_request(message.body)
        except PoisonMessage as poison:
            log.warning("poison_message", extra={"jobId": poison.job_id, "reason": poison.reason})
            await exchange.publish(
                Message(
                    body=_dlq_payload(poison),
                    delivery_mode=DeliveryMode.PERSISTENT,
                    content_type="application/json",
                    headers={"x-death-reason": poison.reason},
                ),
                routing_key=DLQ,
            )
            return

        loop = asyncio.get_running_loop()
        try:
            result: JobResult = await asyncio.wait_for(
                loop.run_in_executor(executor, pipeline.run, req),
                timeout=timeout_s,
            )
        except asyncio.TimeoutError:
            log.warning("job_timeout", extra={"jobId": req.job_id, "timeoutS": timeout_s})
            result = JobResult(
                jobId=req.job_id,
                status=Status.ERROR,
                items=[],
                timingsMs={"total": int(timeout_s * 1000)},
                modelVersion=MODEL_VERSION,
            )
        observe_result(result.status.value, result.timings_ms)
        await exchange.publish(
            Message(
                body=result.to_bytes(),
                delivery_mode=DeliveryMode.PERSISTENT,
                content_type="application/json",
                correlation_id=req.job_id,
            ),
            routing_key=RESULT_QUEUE,
        )


def _dlq_payload(poison: PoisonMessage) -> bytes:
    import json

    return json.dumps(
        {"jobId": poison.job_id, "reason": poison.reason, "status": "ERROR"},
        separators=(",", ":"),
    ).encode()


def main() -> None:
    """Standalone worker process (`python -m mediflow_ai.worker`)."""
    from .logging_utils import setup_logging
    from .pipeline import build_pipeline

    setup_logging()
    settings = Settings.from_env()
    _maybe_start_metrics()
    asyncio.run(run_worker(settings, build_pipeline(settings)))


if __name__ == "__main__":
    main()
