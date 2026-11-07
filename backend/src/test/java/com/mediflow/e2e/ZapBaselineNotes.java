package com.mediflow.e2e;

/**
 * ZAP baseline scan is part of T14 acceptance but must not be run from the agent session (no Docker
 * / no live target). CI should invoke something like:
 *
 * <pre>
 * docker run --rm -t owasp/zap2docker-stable zap-baseline.py \
 *   -t http://host.docker.internal:8080 -r zap-report.html
 * </pre>
 *
 * Fail the pipeline on High/Medium findings; record residual Low/Informational in {@code
 * docs/plan/reports/T14.md}.
 */
public final class ZapBaselineNotes {
  private ZapBaselineNotes() {}
}
