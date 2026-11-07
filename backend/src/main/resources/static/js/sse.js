/**
 * Minimal SSE helper for later prescription AI live updates (T06).
 * Usage: MediFlowSSE.subscribe('/api/rx/jobs/{id}/events', { onMessage, onError })
 */
window.MediFlowSSE = (function () {
  function subscribe(url, handlers) {
    const source = new EventSource(url, { withCredentials: true });
    source.onmessage = function (event) {
      if (handlers && typeof handlers.onMessage === "function") {
        handlers.onMessage(event.data, event);
      }
    };
    source.onerror = function (err) {
      if (handlers && typeof handlers.onError === "function") {
        handlers.onError(err, source);
      }
    };
    return {
      close: function () {
        source.close();
      },
      raw: source,
    };
  }

  return { subscribe: subscribe };
})();
