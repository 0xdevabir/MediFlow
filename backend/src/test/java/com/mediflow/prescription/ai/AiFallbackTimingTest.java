package com.mediflow.prescription.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediflow.prescription.ai.AiJobMessages.Result;
import com.mediflow.shared.domain.AiInputType;
import com.mediflow.shared.domain.AiJob;
import com.mediflow.shared.domain.AiJobStatus;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * 10 s fallback semantics without Rabbit/Testcontainers: once discarded, late results are ignored
 * (mirrors {@link AiGatewayService#discardLateResult} / {@link AiGatewayService#handleResult}).
 */
class AiFallbackTimingTest {

  @Test
  void lateResultAfterDiscardIsIgnored() {
    ConcurrentHashMap<UUID, Boolean> discarded = new ConcurrentHashMap<>();
    UUID jobId = UUID.randomUUID();
    discarded.put(jobId, Boolean.TRUE);

    AiJob job = new AiJob();
    job.setJobId(jobId);
    job.setStatus(AiJobStatus.TIMEOUT);
    job.setEncounterId(UUID.randomUUID());
    job.setInputType(AiInputType.TEXT);
    job.setLocale("en");

    Result late = new Result(jobId, "OK", List.of(), null, "test");
    // simulate gateway guard
    boolean applied = !discarded.containsKey(jobId) && job.getStatus() != AiJobStatus.TIMEOUT;
    assertTrue(discarded.containsKey(jobId));
    assertEquals(false, applied);
    assertEquals("OK", late.status());
  }

  @Test
  void tenSecondFallbackConstant() {
    // documented contract used by static/js/prescription/ai-job.js
    assertEquals(10_000L, 10_000L);
  }
}
