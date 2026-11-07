"""``python -m mediflow_ai`` → uvicorn on :8090."""

from __future__ import annotations

import os


def main() -> None:
    import uvicorn

    host = os.environ.get("MEDIFLOW_AI_HOST", "0.0.0.0")
    port = int(os.environ.get("MEDIFLOW_AI_PORT", "8090"))
    uvicorn.run("mediflow_ai.app:app", host=host, port=port, log_level="info")


if __name__ == "__main__":
    main()
