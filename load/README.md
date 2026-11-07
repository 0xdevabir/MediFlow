# MediFlow load tests (k6)

Write-only in T14 — **do not run** from the agent session (no Docker).

| Script | Goal |
|--------|------|
| `k6-booking-queue.js` | 200 VUs booking/queue; API p95 ≤ 500 ms |
| `k6-ai-job.js` | AI job p95 ≤ 5 s |

```bash
k6 run -e BASE_URL=http://localhost:8080 load/k6-booking-queue.js
k6 run -e MEDIFLOW_AI_URL=http://localhost:8090 load/k6-ai-job.js
```

Record p95 numbers in `docs/plan/reports/T14.md` after a real run.
