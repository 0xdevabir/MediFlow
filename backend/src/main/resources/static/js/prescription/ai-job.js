(function () {
  const MANUAL_AFTER_MS = 10000;

  function csrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
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

  function setLive(text) {
    const live = document.querySelector("#aiLive .mf-live-text") || document.getElementById("aiLive");
    if (live) {
      if (live.classList && live.classList.contains("mf-live-region")) {
        const span = live.querySelector(".mf-live-text");
        if (span) span.textContent = text;
        else live.textContent = text;
      } else {
        live.textContent = text;
      }
    }
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
    setLive(waiting ? waiting.textContent : "Waiting for AI…");

    const res = await fetch("/api/prescriptions/" + rxId + "/ai-jobs", {
      method: "POST",
      credentials: "same-origin",
      headers: csrfHeaders(),
    });
    if (res.status === 503 || res.status >= 400) {
      hide(waiting);
      show(manual);
      setLive(manual ? manual.textContent : "AI unavailable");
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
      if (
        data &&
        (data.manual ||
          data.status === "TIMEOUT" ||
          data.status === "ERROR" ||
          data.status === "UNREADABLE")
      ) {
        show(manual);
        setLive(manual ? manual.textContent : "Continue manually");
      } else {
        setLive("AI draft ready");
        window.location.reload();
      }
    }

    const pollTimer = setInterval(function () {
      poll(jobId, finish);
    }, 1500);

    const fallbackTimer = setTimeout(async function () {
      if (finished) return;
      try {
        await discard(jobId);
      } catch (_) {
        /* ignore */
      }
      finish({ manual: true, status: "TIMEOUT" });
    }, MANUAL_AFTER_MS);

    if (window.MediFlowSSE && typeof window.MediFlowSSE.subscribe === "function") {
      sse = window.MediFlowSSE.subscribe("/api/ai-jobs/" + jobId + "/events", {
        onMessage: function (ev) {
          try {
            const data = typeof ev.data === "string" ? JSON.parse(ev.data) : ev.data;
            if (data && data.status && data.status !== "QUEUED" && data.status !== "RUNNING") {
              finish(data);
            }
          } catch (_) {
            /* ignore parse errors */
          }
        },
        onError: function () {
          /* reconnect handled by sse.js; polling remains as backup */
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
