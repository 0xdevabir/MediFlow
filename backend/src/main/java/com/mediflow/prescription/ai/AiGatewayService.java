package com.mediflow.prescription.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.prescription.ai.AiJobMessages.Item;
import com.mediflow.prescription.ai.AiJobMessages.Request;
import com.mediflow.prescription.ai.AiJobMessages.Result;
import com.mediflow.prescription.domain.RxInputMode;
import com.mediflow.prescription.domain.RxPrescriptionMeta;
import com.mediflow.prescription.draft.DraftService;
import com.mediflow.prescription.repo.RxAiJobQueries;
import com.mediflow.prescription.repo.RxItemQueries;
import com.mediflow.prescription.safety.SafetyEvaluationService;
import com.mediflow.prescription.sse.AiJobSseHub;
import com.mediflow.prescription.support.RxAccess;
import com.mediflow.prescription.support.RxExceptions.AiUnavailableException;
import com.mediflow.prescription.support.RxExceptions.ValidationException;
import com.mediflow.shared.domain.AiInputType;
import com.mediflow.shared.domain.AiJob;
import com.mediflow.shared.domain.AiJobStatus;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiGatewayService {

  private static final Logger log = LoggerFactory.getLogger(AiGatewayService.class);

  /** Jobs the UI has already switched to manual (10 s) — late results are discarded. */
  private final ConcurrentHashMap<UUID, Boolean> discarded = new ConcurrentHashMap<>();

  private final RxAccess access;
  private final DraftService drafts;
  private final RxAiJobQueries jobs;
  private final RxItemQueries items;
  private final SafetyEvaluationService safety;
  private final AiCircuitBreaker breaker;
  private final AiJobSseHub sse;
  private final ObjectProvider<RabbitTemplate> rabbit;
  private final ObjectMapper mapper;
  private final ApplicationEventPublisher events;
  private final AuditPort audit;
  private final ClockPort clock;

  public AiGatewayService(
      RxAccess access,
      DraftService drafts,
      RxAiJobQueries jobs,
      RxItemQueries items,
      SafetyEvaluationService safety,
      AiCircuitBreaker breaker,
      AiJobSseHub sse,
      ObjectProvider<RabbitTemplate> rabbit,
      ObjectMapper mapper,
      ApplicationEventPublisher events,
      AuditPort audit,
      ClockPort clock) {
    this.access = access;
    this.drafts = drafts;
    this.jobs = jobs;
    this.items = items;
    this.safety = safety;
    this.breaker = breaker;
    this.sse = sse;
    this.rabbit = rabbit;
    this.mapper = mapper;
    this.events = events;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  @PreAuthorize("hasRole('DOCTOR')")
  public UUID submitJob(UUID prescriptionId) {
    Prescription rx = drafts.requireDraft(prescriptionId);
    access.requireCanPrescribe(rx.getPatientId(), rx.getEncounterId());
    RxPrescriptionMeta meta = drafts.requireMeta(prescriptionId);

    if (!breaker.allowRequest()) {
      throw new AiUnavailableException("AI circuit open — use manual entry");
    }

    AiInputType inputType;
    String objectKey = null;
    String text = null;
    if (meta.getInputMode() == RxInputMode.PHOTO) {
      inputType = AiInputType.IMAGE;
      objectKey = meta.getPhotoObjectKey();
      if (objectKey == null) {
        throw new ValidationException("rx.error.photo", "No photo uploaded");
      }
    } else if (meta.getInputMode() == RxInputMode.TEXT) {
      inputType = AiInputType.TEXT;
      text = meta.getTypedText();
      if (text == null || text.isBlank()) {
        throw new ValidationException("rx.error.text", "No typed text");
      }
    } else {
      throw new ValidationException("rx.error.ai_mode", "Manual drafts do not use AI");
    }

    UUID jobId = UUID.randomUUID();
    AiJob job = new AiJob();
    job.setJobId(jobId);
    job.setEncounterId(rx.getEncounterId());
    job.setPrescriptionId(prescriptionId);
    job.setInputType(inputType);
    job.setObjectKey(objectKey);
    job.setTextInput(text);
    job.setLocale("en");
    job.setStatus(AiJobStatus.QUEUED);
    jobs.save(job);

    Request request =
        new Request(
            jobId,
            rx.getEncounterId(),
            inputType.name(),
            objectKey,
            text,
            "en");

    RabbitTemplate template = rabbit.getIfAvailable();
    if (template == null) {
      breaker.onFailure();
      job.setStatus(AiJobStatus.ERROR);
      job.setErrorMessage("RabbitMQ unavailable");
      jobs.save(job);
      throw new AiUnavailableException("AI queue unavailable — use manual entry");
    }
    try {
      template.convertAndSend(AiRabbitConfig.EXCHANGE, AiJobMessages.REQUESTS, request);
      breaker.onSuccess();
      sse.publish(jobId, "progress", Map.of("status", "QUEUED"));
    } catch (Exception e) {
      breaker.onFailure();
      job.setStatus(AiJobStatus.ERROR);
      job.setErrorMessage(e.getMessage());
      jobs.save(job);
      throw new AiUnavailableException("Failed to enqueue AI job — use manual entry");
    }

    audit.record(
        access.requireDoctorUserId(),
        rx.getPatientId(),
        "RX_AI_JOB_SUBMIT",
        "AiJob",
        job.getId(),
        Map.of("jobId", jobId.toString()));
    return jobId;
  }

  /** Mark job as discarded after the 10 s UI fallback; late results ignored. */
  @Transactional
  @PreAuthorize("hasRole('DOCTOR')")
  public void discardLateResult(UUID jobId) {
    discarded.put(jobId, Boolean.TRUE);
    jobs.findByJobId(jobId)
        .ifPresent(
            job -> {
              if (job.getStatus() == AiJobStatus.QUEUED || job.getStatus() == AiJobStatus.RUNNING) {
                job.setStatus(AiJobStatus.TIMEOUT);
                jobs.save(job);
              }
            });
    sse.publish(jobId, "timeout", Map.of("status", "TIMEOUT", "manual", true));
    sse.complete(jobId);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('DOCTOR')")
  public Optional<AiJob> getJob(UUID jobId) {
    return jobs.findByJobId(jobId);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> jobPollView(UUID jobId) {
    AiJob job =
        jobs.findByJobId(jobId).orElseThrow(() -> new ValidationException("rx.error.job", "not found"));
    Map<String, Object> m = new HashMap<>();
    m.put("jobId", job.getJobId());
    m.put("status", job.getStatus().name());
    m.put("prescriptionId", job.getPrescriptionId());
    m.put("errorMessage", job.getErrorMessage());
    m.put("discarded", discarded.containsKey(jobId));
    return m;
  }

  public boolean isHealthy() {
    return breaker.healthy() && rabbit.getIfAvailable() != null;
  }

  @Transactional
  public void handleResult(Result result) {
    if (result == null || result.jobId() == null) {
      return;
    }
    UUID jobId = result.jobId();
    if (discarded.containsKey(jobId)) {
      log.info("Discarding late AI result for job {}", jobId);
      return;
    }
    AiJob job = jobs.findByJobId(jobId).orElse(null);
    if (job == null) {
      log.warn("AI result for unknown job {}", jobId);
      return;
    }
    if (job.getStatus() == AiJobStatus.TIMEOUT) {
      log.info("Ignoring result for timed-out job {}", jobId);
      return;
    }

    try {
      job.setResultJson(mapper.writeValueAsString(result));
    } catch (JsonProcessingException e) {
      job.setResultJson("{}");
    }
    job.setModelVersion(result.modelVersion());

    String status = result.status() == null ? "ERROR" : result.status().toUpperCase();
    switch (status) {
      case "OK" -> {
        job.setStatus(AiJobStatus.OK);
        applyItems(job, result.items());
        safety.reevaluate(job.getPrescriptionId());
        events.publishEvent(
            new DomainEvents.AiJobCompleted(jobId, job.getEncounterId(), "OK", clock.now()));
        sse.publish(jobId, "result", Map.of("status", "OK", "itemCount", result.items() == null ? 0 : result.items().size()));
        breaker.onSuccess();
      }
      case "UNREADABLE" -> {
        job.setStatus(AiJobStatus.UNREADABLE);
        job.setErrorMessage("unreadable");
        events.publishEvent(
            new DomainEvents.AiJobFailed(jobId, job.getEncounterId(), "UNREADABLE", clock.now()));
        sse.publish(jobId, "result", Map.of("status", "UNREADABLE", "manual", true));
        breaker.onSuccess(); // service responded
      }
      default -> {
        job.setStatus(AiJobStatus.ERROR);
        job.setErrorMessage(status);
        events.publishEvent(
            new DomainEvents.AiJobFailed(jobId, job.getEncounterId(), status, clock.now()));
        sse.publish(jobId, "result", Map.of("status", "ERROR", "manual", true));
        breaker.onFailure();
      }
    }
    jobs.save(job);
    sse.complete(jobId);
  }

  private void applyItems(AiJob job, List<Item> aiItems) {
    if (aiItems == null || aiItems.isEmpty()) {
      return;
    }
    // Clear previous AI items for a re-run? Keep simple: append only if draft has no items yet.
    if (!items.findByPrescriptionIdOrderByCreatedAtAsc(job.getPrescriptionId()).isEmpty()) {
      log.info("Draft already has items; skipping AI apply for job {}", job.getJobId());
      return;
    }
    int pos = 0;
    for (Item ai : aiItems) {
      var conf = ai.conf();
      drafts.applyAiItem(
          job.getPrescriptionId(),
          job.getJobId(),
          ai.raw(),
          ai.brand(),
          ai.generic(),
          ai.dose(),
          ai.route(),
          ai.frequency(),
          ai.duration(),
          bd(conf == null ? null : conf.drug()),
          bd(conf == null ? null : conf.dose()),
          bd(conf == null ? null : conf.frequency()),
          bd(conf == null ? null : conf.route()),
          bd(conf == null ? null : conf.duration()),
          pos++);
    }
  }

  private static BigDecimal bd(Double v) {
    return v == null ? null : BigDecimal.valueOf(v);
  }

}
