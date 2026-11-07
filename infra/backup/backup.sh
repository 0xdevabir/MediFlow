#!/usr/bin/env bash
# Nightly encrypted Postgres dump + MinIO mirror.
#
# Required env (or Docker secrets mounted under /run/secrets):
#   MEDIFLOW_DB_HOST (default postgres)  MEDIFLOW_DB_PORT (5432)
#   MEDIFLOW_DB_NAME  MEDIFLOW_DB_USER  MEDIFLOW_DB_PASSWORD
#   MEDIFLOW_MINIO_ENDPOINT  MEDIFLOW_MINIO_ACCESS_KEY  MEDIFLOW_MINIO_SECRET_KEY
#   MEDIFLOW_MINIO_BUCKET (source bucket, default mediflow)
#   MEDIFLOW_BACKUP_BUCKET (destination bucket for dumps+mirror, default mediflow-backups)
#   MEDIFLOW_BACKUP_GPG_RECIPIENT  — GPG key id/email for --encrypt
#   OR MEDIFLOW_BACKUP_AGE_RECIPIENT — age recipient (age1...) preferred over GPG when set
#   MEDIFLOW_BACKUP_DIR (default ./out)
#
# Cron example (host or backup sidecar):
#   15 2 * * *  cd /opt/mediflow && MEDIFLOW_BACKUP_DIR=/var/backups/mediflow ./infra/backup/backup.sh
set -euo pipefail

load_secret() {
  local name="$1"
  local cur="${!name-}"
  if [[ -n "$cur" ]]; then printf '%s' "$cur"; return; fi
  if [[ -f "/run/secrets/$name" ]]; then cat "/run/secrets/$name"; return; fi
  if [[ -f "${SECRETS_DIR:-./secrets}/$name" ]]; then cat "${SECRETS_DIR:-./secrets}/$name"; return; fi
  return 1
}

require() { [[ -n "${!1-}" ]] || { echo "missing env: $1" >&2; exit 1; }; }

DB_HOST="${MEDIFLOW_DB_HOST:-postgres}"
DB_PORT="${MEDIFLOW_DB_PORT:-5432}"
DB_NAME="${MEDIFLOW_DB_NAME:-mediflow}"
DB_USER="${MEDIFLOW_DB_USER:-mediflow}"
DB_PASSWORD="$(load_secret MEDIFLOW_DB_PASSWORD || true)"
MINIO_ENDPOINT="${MEDIFLOW_MINIO_ENDPOINT:-http://minio:9000}"
MINIO_ACCESS="$(load_secret MEDIFLOW_MINIO_ACCESS_KEY || true)"
MINIO_SECRET="$(load_secret MEDIFLOW_MINIO_SECRET_KEY || true)"
SRC_BUCKET="${MEDIFLOW_MINIO_BUCKET:-mediflow}"
DST_BUCKET="${MEDIFLOW_BACKUP_BUCKET:-mediflow-backups}"
OUT_DIR="${MEDIFLOW_BACKUP_DIR:-$(cd "$(dirname "$0")" && pwd)/out}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
WORKDIR="$OUT_DIR/$STAMP"

[[ -n "$DB_PASSWORD" ]] || { echo "MEDIFLOW_DB_PASSWORD required" >&2; exit 1; }
[[ -n "$MINIO_ACCESS" && -n "$MINIO_SECRET" ]] || { echo "MinIO credentials required" >&2; exit 1; }

command -v pg_dump >/dev/null || { echo "pg_dump required" >&2; exit 1; }
command -v mc >/dev/null || { echo "MinIO client (mc) required" >&2; exit 1; }

mkdir -p "$WORKDIR"
chmod 700 "$OUT_DIR" "$WORKDIR"

echo "==> pg_dump $DB_NAME@$DB_HOST"
export PGPASSWORD="$DB_PASSWORD"
DUMP="$WORKDIR/postgres-$DB_NAME.sql.gz"
pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  --no-owner --no-acl --format=plain \
  | gzip -9 > "$DUMP"
unset PGPASSWORD

ENCRYPTED=""
if [[ -n "${MEDIFLOW_BACKUP_AGE_RECIPIENT:-}" ]]; then
  command -v age >/dev/null || { echo "age required when MEDIFLOW_BACKUP_AGE_RECIPIENT is set" >&2; exit 1; }
  ENCRYPTED="$DUMP.age"
  age -r "$MEDIFLOW_BACKUP_AGE_RECIPIENT" -o "$ENCRYPTED" "$DUMP"
  shred -u "$DUMP" 2>/dev/null || rm -f "$DUMP"
elif [[ -n "${MEDIFLOW_BACKUP_GPG_RECIPIENT:-}" ]]; then
  command -v gpg >/dev/null || { echo "gpg required when MEDIFLOW_BACKUP_GPG_RECIPIENT is set" >&2; exit 1; }
  ENCRYPTED="$DUMP.gpg"
  gpg --batch --yes --trust-model always \
    --recipient "$MEDIFLOW_BACKUP_GPG_RECIPIENT" \
    --encrypt --output "$ENCRYPTED" "$DUMP"
  shred -u "$DUMP" 2>/dev/null || rm -f "$DUMP"
else
  echo "WARN: no MEDIFLOW_BACKUP_AGE_RECIPIENT / MEDIFLOW_BACKUP_GPG_RECIPIENT — dump left unencrypted at $DUMP" >&2
  ENCRYPTED="$DUMP"
fi

echo "==> mc alias + ensure backup bucket"
mc alias set mfbackup "$MINIO_ENDPOINT" "$MINIO_ACCESS" "$MINIO_SECRET" >/dev/null
mc mb --ignore-existing "mfbackup/$DST_BUCKET" >/dev/null

echo "==> upload encrypted dump"
mc cp "$ENCRYPTED" "mfbackup/$DST_BUCKET/postgres/$STAMP/$(basename "$ENCRYPTED")"

echo "==> mirror object bucket $SRC_BUCKET -> $DST_BUCKET/objects/"
mc mirror --overwrite --remove --preserve \
  "mfbackup/$SRC_BUCKET" "mfbackup/$DST_BUCKET/objects/$STAMP"

# Keep 14 local stamps; object-store retention is an ops policy (lifecycle rules).
if [[ "${MEDIFLOW_BACKUP_KEEP_LOCAL:-14}" =~ ^[0-9]+$ ]]; then
  ls -1dt "$OUT_DIR"/20* 2>/dev/null | tail -n +"$((MEDIFLOW_BACKUP_KEEP_LOCAL + 1))" | xargs -r rm -rf
fi

echo "OK backup $STAMP"
echo "  dump:   mfbackup/$DST_BUCKET/postgres/$STAMP/$(basename "$ENCRYPTED")"
echo "  mirror: mfbackup/$DST_BUCKET/objects/$STAMP"
