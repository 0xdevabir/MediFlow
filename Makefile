# MediFlow local stack. Needs Docker Desktop running.
#   make up      build + start everything (infra, backend, AI) → http://localhost:8080
#   make down    stop everything (keeps data)
#   make help    all targets

COMPOSE := docker compose -f infra/docker-compose.yml -f infra/docker-compose.app.yml --env-file .env
INFRA   := docker compose -f infra/docker-compose.yml --env-file .env

.DEFAULT_GOAL := help
.PHONY: help env up down restart logs logs-backend logs-ai ps build clean infra infra-down run-backend

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-14s\033[0m %s\n",$$1,$$2}'

env: .env ## Create .env from .env.example (with generated keys) if missing

.env:
	@cp .env.example .env
	@KEY=$$(openssl rand -base64 32); sed -i.bak "s|^MEDIFLOW_FIELD_ENCRYPTION_KEY=.*|MEDIFLOW_FIELD_ENCRYPTION_KEY=$$KEY|" .env
	@grep -q '^MEDIFLOW_INTERNAL_API_KEY=' .env || echo "MEDIFLOW_INTERNAL_API_KEY=$$(openssl rand -hex 24)" >> .env
	@rm -f .env.bak
	@echo "Created .env (fresh encryption + internal API keys)"

up: .env ## Build and start the full stack
	$(COMPOSE) up -d --build
	@echo ""
	@echo "  App        http://localhost:8080   (login: admin / password, doctor / password, patient / password ...)"
	@echo "  AI service http://localhost:8000/health"
	@echo "  RabbitMQ   http://localhost:15672  (mediflow / mediflow)"
	@echo "  MinIO      http://localhost:9001   (mediflow / mediflowsecret)"
	@echo "  Mailpit    http://localhost:8025"
	@echo "  Backend takes ~1-2 min on first start (Flyway migrations). Follow with: make logs-backend"

down: ## Stop everything (data volumes kept)
	$(COMPOSE) down

restart: down up ## Restart the full stack

build: .env ## Rebuild images without starting
	$(COMPOSE) build

ps: ## Show container status
	$(COMPOSE) ps

logs: ## Follow logs of all services
	$(COMPOSE) logs -f --tail=100

logs-backend: ## Follow backend logs
	$(COMPOSE) logs -f --tail=200 backend

logs-ai: ## Follow AI api + worker logs
	$(COMPOSE) logs -f --tail=200 ai-api ai-worker

clean: ## Stop everything AND delete data volumes (DB, MinIO)
	$(COMPOSE) down -v --remove-orphans

infra: .env ## Start only infra (Postgres, Redis, RabbitMQ, MinIO, Mailpit) for running the backend from your IDE
	$(INFRA) up -d

infra-down: ## Stop infra only
	$(INFRA) down

run-backend: ## Run backend locally with ./mvnw (needs `make infra` first)
	cd backend && set -a && . ../.env && set +a && \
	  JAVA_HOME=$${JAVA_HOME:-/opt/homebrew/opt/openjdk@21} ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,pharmacy
