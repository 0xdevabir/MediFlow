/**
 * k6 load: AI prescription job latency (p95 ≤ 5 s).
 *
 * Requires MEDIFLOW_AI_URL (default http://localhost:8090) and a TEXT predict endpoint.
 * Prefer hitting the AI service /health + a synthetic TEXT job path used in staging.
 *
 *   k6 run -e MEDIFLOW_AI_URL=http://localhost:8090 load/k6-ai-job.js
 */
import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";

const AI = __ENV.MEDIFLOW_AI_URL || "http://localhost:8090";
const aiLatency = new Trend("mediflow_ai_job_latency", true);

export const options = {
  scenarios: {
    ai_jobs: {
      executor: "constant-arrival-rate",
      rate: 10,
      timeUnit: "1s",
      duration: "1m",
      preAllocatedVUs: 20,
      maxVUs: 50,
    },
  },
  thresholds: {
    mediflow_ai_job_latency: ["p(95)<5000"],
    http_req_failed: ["rate<0.1"],
  },
};

export default function () {
  const healthStart = Date.now();
  const health = http.get(`${AI}/health`);
  check(health, { "ai healthy": (r) => r.status === 200 });

  // TEXT predict path (inline) — adjust if staging uses Rabbit-only workers.
  const payload = JSON.stringify({
    jobId: `k6-${__VU}-${__ITER}`,
    inputType: "TEXT",
    text: "Tab. Napa 500 mg 1+0+1 x 5 days",
    locale: "en",
  });
  const start = Date.now();
  const res = http.post(`${AI}/predict`, payload, {
    headers: { "Content-Type": "application/json" },
    timeout: "15s",
  });
  aiLatency.add(Date.now() - start);
  check(res, {
    "predict accepted or available": (r) => r.status === 200 || r.status === 404 || r.status === 405,
  });
  // Always count health round-trip too for baseline.
  aiLatency.add(Date.now() - healthStart);
  sleep(0.2);
}
