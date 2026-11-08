# MediFlow AI

Security-first outpatient coordination platform (Patient → Front desk → Nurse → Doctor → Lab → Pharmacy → Follow-up) with an asynchronous AI e-prescription pipeline.

## Prerequisites

- Java 21 (`JAVA_HOME=/opt/homebrew/opt/openjdk@21`)
- Docker / Docker Compose
- Maven Wrapper (`backend/mvnw`)

## Quick start: one command

```bash
make up        # creates .env if missing, builds + starts infra, backend, AI api + worker
make logs-backend
make down      # stop (data kept) · make clean = also wipe DB/MinIO volumes · make help = all targets
```

App: http://localhost:8080 · AI: http://localhost:8000/health · Mailpit: http://localhost:8025 · RabbitMQ: http://localhost:15672 · MinIO: http://localhost:9001

## Quick start (backend from IDE)

`make infra` then `make run-backend`, or manually:

```bash
# 1. Infrastructure
cp .env.example .env
docker compose -f infra/docker-compose.yml --env-file .env up -d

# 2. Backend
cd backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
export MEDIFLOW_FIELD_ENCRYPTION_KEY="$(openssl rand -base64 32)"
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Open http://localhost:8080 — form login.

### Dev seed users (`dev` profile only)

| Username | Role | Password |
|---|---|---|
| patient | PATIENT | password |
| doctor | DOCTOR | password |
| nurse | NURSE | password |
| frontdesk | FRONT_DESK | password |
| labtech | LAB_TECH | password |
| pharmacist | PHARMACIST | password |
| er | ER | password |
| admin | ADMIN | password |
| approver | CLINICAL_APPROVER | password |

## Tests

```bash
cd backend
./mvnw verify
```

Requires Docker for Testcontainers (PostgreSQL 16). Unit tests are `*Test` (surefire);
Spring/Testcontainers integration tests are `*IT` (failsafe, run by `verify`) and should extend
`com.mediflow.support.AbstractIntegrationTest` (one shared Postgres container, `test` profile with
Redis/Session/RabbitMQ/mail auto-config disabled).

## Layout

- `backend/` — Spring Boot modular monolith (Java 21)
- `ai-service/` — FastAPI AI worker (skeleton; implemented in T09)
- `infra/` — Docker Compose for local dependencies
- `docs/plan/` — implementation plan and task prompts
- `research/` — source R&D / technical docs

## Safety invariants

See `docs/plan/PLAN.md` §6. AI never signs, dispenses, or writes the clinical record directly. Unknown is never safe.
