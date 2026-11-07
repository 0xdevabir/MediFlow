package com.mediflow.appointment.queue;

import com.mediflow.appointment.support.QueueChanged;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** In-process fan-out for live queue pages (SSE). Polling remains the fallback. */
@Component
public class QueueSseHub {

  private static final Logger log = LoggerFactory.getLogger(QueueSseHub.class);
  private static final long TIMEOUT_MS = 30 * 60 * 1000L;

  private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

  public SseEmitter subscribe(UUID doctorId, LocalDate queueDate) {
    String key = key(doctorId, queueDate);
    SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
    emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
    emitter.onCompletion(() -> remove(key, emitter));
    emitter.onTimeout(() -> remove(key, emitter));
    emitter.onError(ex -> remove(key, emitter));
    try {
      emitter.send(SseEmitter.event().name("connected").data("ok"));
    } catch (IOException e) {
      remove(key, emitter);
    }
    return emitter;
  }

  public void publish(QueueChanged changed) {
    String key = key(changed.doctorId(), changed.queueDate());
    List<SseEmitter> list = emitters.getOrDefault(key, List.of());
    for (SseEmitter emitter : list) {
      try {
        emitter.send(
            SseEmitter.event()
                .name("queue")
                .data(
                    Map.of(
                        "doctorId", changed.doctorId().toString(),
                        "queueDate", changed.queueDate().toString())));
      } catch (IOException e) {
        remove(key, emitter);
        log.debug("SSE client gone for {}", key);
      }
    }
  }

  private void remove(String key, SseEmitter emitter) {
    List<SseEmitter> list = emitters.get(key);
    if (list != null) {
      list.remove(emitter);
    }
  }

  private static String key(UUID doctorId, LocalDate queueDate) {
    return doctorId + "|" + queueDate;
  }
}
