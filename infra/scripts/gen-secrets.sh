#!/usr/bin/env bash
# Generates random Docker-secret files for infra/docker-compose.prod.yml into infra/secrets/
# (gitignored). Existing files are kept, so re-running is safe. Rotate by deleting a file.
#
# File names are the property / env names the services read:
#   - backend imports /run/secrets via SPRING_CONFIG_IMPORT=configtree, so a file named
#     MEDIFLOW_DB_PASSWORD resolves ${MEDIFLOW_DB_PASSWORD} in application.yml
#   - ai-service's entrypoint exports /run/secrets/MEDIFLOW_* as env vars
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)/secrets"
mkdir -p "$DIR"
chmod 700 "$DIR"
command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }

rand() { openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c "${1:-32}"; }

put() { # name value  (no trailing newline - configtree / *_FILE readers take the file verbatim)
  local f="$DIR/$1"
  if [[ -f "$f" ]]; then echo "keep   $1"; return; fi
  printf '%s' "$2" > "$f"
  # bind-mounted into non-root containers with differing uids -> world-readable file, private dir
  chmod 644 "$f"
  echo "create $1"
}

put MEDIFLOW_DB_PASSWORD          "$(rand 32)"
put MEDIFLOW_RABBIT_PASSWORD      "$(rand 32)"
put MEDIFLOW_REDIS_PASSWORD       "$(rand 32)"
put MEDIFLOW_MINIO_ACCESS_KEY     "mediflow-$(rand 8)"
put MEDIFLOW_MINIO_SECRET_KEY     "$(rand 40)"
put MEDIFLOW_FIELD_ENCRYPTION_KEY "$(openssl rand -base64 32)"
put MINIO_KMS_SECRET_KEY          "mediflow-sse:$(openssl rand -base64 32)"
put GRAFANA_ADMIN_PASSWORD        "$(rand 24)"

# Spring Data Redis reads spring.data.redis.password (no MEDIFLOW_ placeholder in application.yml).
if [[ ! -f "$DIR/spring.data.redis.password" ]]; then
  cp "$DIR/MEDIFLOW_REDIS_PASSWORD" "$DIR/spring.data.redis.password"; echo "create spring.data.redis.password"
fi

# RabbitMQ: credentials as a conf.d fragment (avoids passwords in env/process args).
if [[ ! -f "$DIR/rabbitmq-credentials.conf" ]]; then
  {
    echo "default_user = ${MEDIFLOW_RABBIT_USER:-mediflow}"
    echo "default_pass = $(cat "$DIR/MEDIFLOW_RABBIT_PASSWORD")"
  } > "$DIR/rabbitmq-credentials.conf"
  chmod 644 "$DIR/rabbitmq-credentials.conf"
  echo "create rabbitmq-credentials.conf"
fi

echo "Secrets in $DIR - back them up offline (losing MEDIFLOW_FIELD_ENCRYPTION_KEY or"
echo "MINIO_KMS_SECRET_KEY makes encrypted data unrecoverable)."
