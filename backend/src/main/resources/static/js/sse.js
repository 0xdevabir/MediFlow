/**
 * Shared SSE helper (V2-01). Usage:
 *   MediFlowSSE.subscribe('/api/rx/jobs/{id}/events', { onMessage, onError, onOpen })
 * Optional reconnect with backoff when the browser closes the stream unexpectedly.
 */
window.MediFlowSSE = (function () {
  function subscribe(url, handlers) {
    handlers = handlers || {};
    var retryMs = 1000;
    var maxRetryMs = 15000;
    var closed = false;
    var source = null;

    function connect() {
      if (closed) {
        return;
      }
      source = new EventSource(url, { withCredentials: true });
      source.onopen = function (event) {
        retryMs = 1000;
        if (typeof handlers.onOpen === "function") {
          handlers.onOpen(event, source);
        }
      };
      source.onmessage = function (event) {
        if (typeof handlers.onMessage === "function") {
          handlers.onMessage(event.data, event);
        }
      };
      source.onerror = function (err) {
        if (typeof handlers.onError === "function") {
          handlers.onError(err, source);
        }
        if (closed) {
          return;
        }
        try {
          source.close();
        } catch (e) {
          /* ignore */
        }
        var wait = retryMs;
        retryMs = Math.min(maxRetryMs, Math.floor(retryMs * 1.6));
        setTimeout(connect, wait);
      };
    }

    connect();

    return {
      close: function () {
        closed = true;
        if (source) {
          source.close();
        }
      },
      raw: function () {
        return source;
      },
    };
  }

  return { subscribe: subscribe };
})();
