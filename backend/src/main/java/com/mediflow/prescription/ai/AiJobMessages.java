package com.mediflow.prescription.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Wire contract with T09 (CONVENTIONS — AI service contract). No patient identifiers. */
public final class AiJobMessages {

  public static final String REQUESTS = "ai.rx.requests";
  public static final String RESULTS = "ai.rx.results";
  public static final String DLQ = "ai.rx.dlq";

  private AiJobMessages() {}

  public record Request(
      UUID jobId, UUID encounterId, String inputType, String objectKey, String text, String locale) {}

  public record Conf(
      Double drug, Double dose, Double frequency, Double route, Double duration) {}

  public record Item(
      String raw,
      String brand,
      String generic,
      String atc,
      String strength,
      String form,
      String dose,
      String route,
      String frequency,
      String duration,
      Conf conf) {}

  public record Result(
      UUID jobId,
      String status,
      List<Item> items,
      Map<String, Long> timingsMs,
      String modelVersion) {}
}
