/**
 * k6 load: 200 concurrent users booking / queue.
 *
 * Run later (needs live stack):
 *   k6 run -e BASE_URL=http://localhost:8080 load/k6-booking-queue.js
 *
 * Targets (PLAN / T14): API p95 ≤ 500 ms; rule engine ≤ 300 ms (measured separately);
 * AI p95 ≤ 5 s (see k6-ai-job.js).
 */
import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const apiLatency = new Trend("mediflow_api_latency", true);

export const options = {
  scenarios: {
    booking_queue: {
      executor: "constant-vus",
      vus: 200,
      duration: "2m",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
    mediflow_api_latency: ["p(95)<500"],
  },
};

function login(username) {
  const jar = http.cookieJar();
  const loginPage = http.get(`${BASE}/login`);
  const csrf =
    (loginPage.body.match(/name="_csrf"\s+value="([^"]+)"/) || [])[1] ||
    (loginPage.body.match(/name="_csrf" value="([^"]+)"/) || [])[1];
  const res = http.post(
    `${BASE}/login`,
    {
      username,
      password: "password",
      _csrf: csrf,
    },
    { redirects: 0, jar },
  );
  return { jar, ok: res.status === 302 || res.status === 200 };
}

export default function () {
  const users = ["patient", "frontdesk", "doctor", "nurse"];
  const user = users[__VU % users.length];
  const { jar, ok } = login(user);
  check(null, { "login ok": () => ok });

  const paths =
    user === "patient"
      ? ["/appointments", "/directory/search", "/notifications"]
      : user === "doctor"
        ? ["/queue/doctor", "/api/queue/board", "/appointments"]
        : user === "nurse"
          ? ["/queue", "/api/queue/board"]
          : ["/appointments/check-in", "/appointments/book", "/queue"];

  for (const path of paths) {
    const start = Date.now();
    const res = http.get(`${BASE}${path}`, { jar });
    apiLatency.add(Date.now() - start);
    check(res, {
      "status not 5xx": (r) => r.status < 500,
    });
  }
  sleep(0.5);
}
