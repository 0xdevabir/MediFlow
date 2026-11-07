package com.mediflow.prescription.web;

import com.mediflow.prescription.ai.AiGatewayService;
import com.mediflow.prescription.sse.AiJobSseHub;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('DOCTOR')")
public class AiJobApiController {

  private final AiGatewayService gateway;
  private final AiJobSseHub sse;

  public AiJobApiController(AiGatewayService gateway, AiJobSseHub sse) {
    this.gateway = gateway;
    this.sse = sse;
  }

  @PostMapping("/prescriptions/{id}/ai-jobs")
  public ResponseEntity<Map<String, Object>> submit(@PathVariable("id") UUID prescriptionId) {
    UUID jobId = gateway.submitJob(prescriptionId);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", jobId));
  }

  @GetMapping(path = "/ai-jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(@PathVariable UUID jobId) {
    return sse.subscribe(jobId);
  }

  @GetMapping("/ai-jobs/{jobId}")
  public Map<String, Object> poll(@PathVariable UUID jobId) {
    return gateway.jobPollView(jobId);
  }

  @PostMapping("/ai-jobs/{jobId}/discard")
  public Map<String, Object> discard(@PathVariable UUID jobId) {
    gateway.discardLateResult(jobId);
    return Map.of("status", "TIMEOUT", "manual", true);
  }

  @GetMapping("/ai/health")
  public Map<String, Object> health() {
    return Map.of("healthy", gateway.isHealthy());
  }
}
