# T13 — DevOps, CI/CD, TLS, monitoring (Wave 2)

Read PLAN. Research: DOCX §7.2 DevOps row, §11.1 transit/resilience rows.

## Owns
`infra/**` (except dev compose basics already from T00 — extend them), `.github/workflows/**`, `backend/Dockerfile`, `ai-service/Dockerfile` (review/tune only).

## Build
1. Multi-stage Dockerfiles (non-root, distroless/slim), `infra/docker-compose.prod.yml` with backend, ai-service (N workers), postgres, redis, rabbitmq, minio, nginx, prometheus, grafana.
2. Nginx: TLS 1.3 only, HSTS, security headers, rate limiting on login/booking/API, gzip, SSE-friendly proxy settings (`proxy_buffering off` for `/api/ai-jobs/*/events` and queue events).
3. Internal service traffic: mTLS or at least isolated network; secrets via env/Docker secrets.
4. Prometheus scrape configs (Actuator, ai-service); Grafana dashboards: API p95, AI p95 per stage, queue depth, SSE connections, error rates, Red Alert count.
5. Alert rules: AI service down, queue backlog, audit chain broken, p95 breaches (API 500 ms, AI 5 s).
6. GitHub Actions: backend `mvn verify` (Testcontainers), ai-service pytest + lint, dependency scanning (OWASP dependency-check / pip-audit), container scan (Trivy), build & push images on `main`; ZAP baseline job against compose stack (nightly).
7. Backup: nightly encrypted `pg_dump` + MinIO mirror script, documented restore drill.

## Acceptance
- `docker compose -f infra/docker-compose.prod.yml up` brings everything up healthy locally with self-signed certs; CI green.
