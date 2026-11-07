/**
 * Live OPD queue: SSE with 10s polling fallback (T04).
 * Configure via data-* on the script tag: data-mode=board|patient,
 * data-doctor-id, data-queue-date, data-appointment-id.
 */
(function () {
  var script = document.currentScript;
  if (!script) return;
  var mode = script.getAttribute("data-mode") || "board";
  var doctorId = script.getAttribute("data-doctor-id");
  var queueDate = script.getAttribute("data-queue-date");
  var appointmentId = script.getAttribute("data-appointment-id");
  var pollMs = 10000;
  var sse = null;

  function reloadBoard() {
    window.location.reload();
  }

  function refreshPatient() {
    if (!appointmentId) return;
    fetch("/api/queue/appointments/" + appointmentId + "/status", {
      credentials: "same-origin",
    })
      .then(function (r) {
        return r.json();
      })
      .then(function (data) {
        if (!data.present) {
          window.location.reload();
          return;
        }
        var token = document.getElementById("tokenNo");
        if (token) token.textContent = data.tokenNo;
        var ahead = document.getElementById("aheadLine");
        if (ahead) ahead.textContent = data.ahead + " patients ahead";
        if (!doctorId && data.doctorId) {
          doctorId = data.doctorId;
          queueDate = data.queueDate;
          connectSse();
        }
      })
      .catch(function () {});
  }

  function connectSse() {
    if (!doctorId || !window.MediFlowSSE) return;
    var url = "/api/queue/stream?doctorId=" + encodeURIComponent(doctorId);
    if (queueDate) url += "&date=" + encodeURIComponent(queueDate);
    if (sse && sse.close) sse.close();
    sse = window.MediFlowSSE.subscribe(url, {
      onMessage: function () {
        if (mode === "patient") refreshPatient();
        else reloadBoard();
      },
      onError: function () {},
    });
  }

  if (mode === "patient") {
    refreshPatient();
    setInterval(refreshPatient, pollMs);
  } else if (doctorId) {
    connectSse();
    setInterval(reloadBoard, pollMs);
  }
})();
