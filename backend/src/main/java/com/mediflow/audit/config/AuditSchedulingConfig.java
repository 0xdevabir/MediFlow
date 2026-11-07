package com.mediflow.audit.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the audit jobs (chain verification, anomaly scan). {@code @EnableScheduling} is global
 * and idempotent, so other modules declaring it too is harmless. Turn the jobs off with {@code
 * mediflow.audit.scheduling.enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "mediflow.audit.scheduling.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuditSchedulingConfig {}
