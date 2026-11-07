"""Timeout behaviour and poison→DLQ helpers (no live RabbitMQ)."""

from __future__ import annotations

import asyncio
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import AsyncMock, MagicMock

import pytest

from mediflow_ai.contract import JobRequest, InputType, PoisonMessage, Status, parse_request
from mediflow_ai.worker import _handle


class _SlowPipe:
    def run(self, req):  # noqa: ANN001
        import time

        time.sleep(0.3)
        raise AssertionError("should have timed out")


@pytest.mark.asyncio
async def test_job_timeout_returns_error_result():
    message = MagicMock()
    message.body = (
        b'{"jobId":"slow","encounterId":"e","inputType":"TEXT","text":"Napa OD","locale":"en"}'
    )
    message.process = MagicMock()
    message.process.return_value.__aenter__ = AsyncMock(return_value=None)
    message.process.return_value.__aexit__ = AsyncMock(return_value=None)

    published: list[tuple[bytes, str]] = []

    async def publish(msg, routing_key):  # noqa: ANN001
        published.append((msg.body, routing_key))

    exchange = MagicMock()
    exchange.publish = AsyncMock(side_effect=publish)

    executor = ThreadPoolExecutor(max_workers=1)
    try:
        await _handle(message, exchange, _SlowPipe(), executor, timeout_s=0.05)
    finally:
        executor.shutdown(wait=False, cancel_futures=True)

    assert len(published) == 1
    body, key = published[0]
    assert key == "ai.rx.results"
    assert b'"status":"ERROR"' in body
    assert b'"jobId":"slow"' in body


@pytest.mark.asyncio
async def test_poison_goes_to_dlq():
    message = MagicMock()
    message.body = b"not-json"
    message.process = MagicMock()
    message.process.return_value.__aenter__ = AsyncMock(return_value=None)
    message.process.return_value.__aexit__ = AsyncMock(return_value=None)

    published: list[str] = []

    async def publish(msg, routing_key):  # noqa: ANN001
        published.append(routing_key)

    exchange = MagicMock()
    exchange.publish = AsyncMock(side_effect=publish)

    executor = ThreadPoolExecutor(max_workers=1)
    try:
        await _handle(message, exchange, _SlowPipe(), executor, timeout_s=1.0)
    finally:
        executor.shutdown(wait=False, cancel_futures=True)

    assert published == ["ai.rx.dlq"]


def test_parse_request_poison_type():
    with pytest.raises(PoisonMessage):
        parse_request(b"{}")
