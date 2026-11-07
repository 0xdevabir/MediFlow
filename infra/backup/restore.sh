#!/usr/bin/env bash
# Restore drill: decrypt a dump, restore into a *scratch* database, verify row counts.
# NEVER point this at production without an explicit MEDIFLOW_RESTORE_CONFIRM=YES.
#
# Usage:
#   MEDIFLOW_RESTORE_CONFIRM=YES \
#   MEDIFLOW_RESTORE_DUMP=/path/to/postgres-mediflow.sql.gz.age \
#   MEDIFLOW_RESTORE_DB=mediflow_restore_drill \
#   ./infra/backup/restore.sh
#
# For age-encrypted dumps set MEDIFLOW_BACKUP_AGE_KEY (path to age identity).
# For gpg-encrypted dumps, the local gpg private keyring must contain the recipient key.
set -euo pipefail

[[ "${MEDIFLOW_RESTORE_CONFIRM:-}" == "YES" ]] || {
  echo "Refusing to run: set MEDIFLOW_RESTORE_CONFIRM=YES after reading infra/backup/RESTORE.md" >&2
  exit 2
}

DUMP="${MEDIFLOW_RESTORE_DUMP:?set MEDIFLOW_RESTORE_DUMP to the encrypted dump path}"
DB_HOST="${MEDIFLOW_DB_HOST:-localhost}"
DB_PORT="${MEDIFLOW_DB_PORT:-5432}"
DB_USER="${MEDIFLOW_DB_USER:-mediflow}"
DB_PASSWORD="${MEDIFLOW_DB_PASSWORD:?}"
RESTORE_DB="${MEDIFLOW_RESTORE_DB:-mediflow_restore_drill}"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

PLAIN="$WORKDIR/dump.sql"

echo "==> decrypt"
case "$DUMP" in
  *.age)
    command -v age >/dev/null
    [[ -n "${MEDIFLOW_BACKUP_AGE_KEY:-}" ]] || { echo "MEDIFLOW_BACKUP_AGE_KEY required" >&2; exit 1; }
    age -d -i "$MEDIFLOW_BACKUP_AGE_KEY" -o "$WORKDIR/dump.sql.gz" "$DUMP"
    gzip -dc "$WORKDIR/dump.sql.gz" > "$PLAIN"
    ;;
  *.gpg)
    gpg --batch --yes --decrypt --output "$WORKDIR/dump.sql.gz" "$DUMP"
    gzip -dc "$WORKDIR/dump.sql.gz" > "$PLAIN"
    ;;
  *.sql.gz)
    gzip -dc "$DUMP" > "$PLAIN"
    ;;
  *.sql)
    cp "$DUMP" "$PLAIN"
    ;;
  *)
    echo "unknown dump format: $DUMP" >&2
    exit 1
    ;;
esac

export PGPASSWORD="$DB_PASSWORD"
echo "==> recreate scratch DB $RESTORE_DB on $DB_HOST"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 <<SQL
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$RESTORE_DB' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS $RESTORE_DB;
CREATE DATABASE $RESTORE_DB OWNER $DB_USER;
SQL

echo "==> restore"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$RESTORE_DB" -v ON_ERROR_STOP=1 -f "$PLAIN" >/dev/null

echo "==> verify (sample counts)"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$RESTORE_DB" -v ON_ERROR_STOP=1 <<'SQL'
SELECT 'app_user' AS entity, count(*) FROM app_user
UNION ALL SELECT 'patient', count(*) FROM patient
UNION ALL SELECT 'audit_event', count(*) FROM audit_event
ORDER BY 1;
SQL

unset PGPASSWORD
echo "OK restore drill into $RESTORE_DB — drop it when finished:"
echo "  DROP DATABASE $RESTORE_DB;"
