/**
 * AI job client: POST → 202+jobId, SSE progress, poll fallback, 10 s → manual.
 * Late results are discarded via POST /api/ai-jobs/{id}/discard.
 */
(function () {
  const FALLBACK_MS = 10000;

  function csrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    // fall back to cookie/form: Spring CookieCsrfTokenRepository exposes XSRF-TOKEN
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
    const headers = { Accept: "application/json" };
    if (match) {
      headers["X-XSRF-TOKEN"] = decodeURIComponent(match[1]);
    }
    if (token && header) {
      headers[header.content] = token.content;
    }
    return headers;
  }

  function show(el) {
    if (el) el.classList.remove("d-none");
  }
  function hide(el) {
    if (el) el.classList.add("d-none");
  }

  async function discard(jobId) {
    await fetch("/api/ai-jobs/" + jobId + "/discard", {
      method: "POST",
      credentials: "same-origin",
      headers: csrfHeaders(),
    });
  }

  async function poll(jobId, onDone) {
    try {
      const res = await fetch("/api/ai-jobs/" + jobId, {
        credentials: "same-origin",
        headers: csrfHeaders(),
      });
      if (!res.ok) return;
      const data = await res.json();
      if (data.status && data.status !== "QUEUED" && data.status !== "RUNNING") {
        onDone(data);
      }
    } catch (_) {
      /* ignore */
    }
  }

  async function start(rxId) {
    const waiting = document.getElementById("aiBanner");
    const manual = document.getElementById("aiManualBanner");
    hide(manual);
    show(waiting);

    const res = await fetch("/api/prescriptions/" + rxId + "/ai-jobs", {
      method: "POST",
      credentials: "same-origin",
      headers: csrfHeaders(),
    });
    if (res.status === 503 || res.status >= 400) {
      hide(waiting);
      show(manual);
      return;
    }
    const body = await res.json();
    const jobId = body.jobId;
    let finished = false;
    let sse;

    function finish(data) {
      if (finished) return;
      finished = true;
      hide(waiting);
      if (sse && sse.close) sse.close();
      clearInterval(pollTimer);
      clearTimeout(fallbackTimer);
      if (data && (data.manual || data.status === "TIMEOUT" || data.status === "ERROR" || data.status === "UNREADABLE")) {
        show(manual);
      } else {
        window.location.reload();
      }
    }

    const fallbackTimer = setTimeout(async function () {
      if (finished) return;
      await discard(jobId);
      finish({ manual: true, status: "TIMEOUT" });
    }, FALLBACK_MS);

    const pollTimer = setInterval(function () {
      if (!finished) poll(jobId, finish);
    }, 1500);

    if (window.MediFlowSSE) {
      sse = MediFlowSSE.subscribe("/api/ai-jobs/" + jobId + "/events", {
        onMessage: function (raw) {
          try {
            const data = typeof raw === "string" ? JSON.parse(raw) : raw;
            if (data.status && data.status !== "QUEUED" && data.status !== "RUNNING") {
              finish(data);
            }
          } catch (_) {
            /* progress text */
          }
        },
        onError: function () {
          /* poll continues */
        },
      });
    }
  }

  document.addEventListener("DOMContentLoaded", function () {
    const btn = document.getElementById("runAiBtn");
    if (!btn) return;
    btn.addEventListener("click", function () {
      const rxId = btn.getAttribute("data-rx-id");
      if (rxId) start(rxId);
    });
  });
})();
