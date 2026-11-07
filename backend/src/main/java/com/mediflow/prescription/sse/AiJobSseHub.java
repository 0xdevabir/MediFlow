package com.mediflow.prescription.sse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** In-memory fan-out of AI job progress/result events to SSE subscribers. */
@Component
public class AiJobSseHub {

  private static final long TIMEOUT_MS = 120_000L;

  private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

  public SseEmitter subscribe(UUID jobId) {
    SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
    emitters.computeIfAbsent(jobId, id -> new CopyOnWriteArrayList<>()).add(emitter);
    emitter.onCompletion(() -> remove(jobId, emitter));
    emitter.onTimeout(() -> remove(jobId, emitter));
    emitter.onError(e -> remove(jobId, emitter));
    return emitter;
  }

  public void publish(UUID jobId, String eventName, Object data) {
    List<SseEmitter> list = emitters.get(jobId);
    if (list == null || list.isEmpty()) {
      return;
    }
    for (SseEmitter emitter : list) {
      try {
        emitter.send(SseEmitter.event().name(eventName).data(data));
      } catch (IOException ex) {
        emitter.completeWithError(ex);
        remove(jobId, emitter);
      }
    }
  }

  public void complete(UUID jobId) {
    List<SseEmitter> list = emitters.remove(jobId);
    if (list == null) {
      return;
    }
    for (SseEmitter emitter : list) {
      try {
        emitter.complete();
      } catch (Exception ignored) {
        // already completed
      }
    }
  }

  private void remove(UUID jobId, SseEmitter emitter) {
    List<SseEmitter> list = emitters.get(jobId);
    if (list != null) {
      list.remove(emitter);
      if (list.isEmpty()) {
        emitters.remove(jobId);
      }
    }
  }
}
