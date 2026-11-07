# MediFlow backup restore drill

## What is backed up

Nightly `infra/backup/backup.sh`:

1. **Postgres** — `pg_dump` (plain SQL), gzipped, then encrypted with **age** (preferred) or **GPG**.
2. **MinIO** — `mc mirror` of `MEDIFLOW_MINIO_BUCKET` into `MEDIFLOW_BACKUP_BUCKET/objects/<stamp>/`.

Secrets used for encryption (`MEDIFLOW_BACKUP_AGE_RECIPIENT` / GPG private key, field-encryption key, MinIO KMS key) are **not** stored in the backup bucket. Losing them makes PHI unrecoverable.

## Quarterly drill (checklist)

1. Provision a scratch Postgres (empty instance or dedicated database host). **Do not** restore onto the live primary.
2. Fetch the latest dump object, e.g.  
   `mc cp mfbackup/mediflow-backups/postgres/<stamp>/postgres-mediflow.sql.gz.age /tmp/`
3. Run:

```bash
export MEDIFLOW_RESTORE_CONFIRM=YES
export MEDIFLOW_RESTORE_DUMP=/tmp/postgres-mediflow.sql.gz.age
export MEDIFLOW_BACKUP_AGE_KEY=/secure/path/backup-age.key
export MEDIFLOW_DB_HOST=127.0.0.1
export MEDIFLOW_DB_USER=mediflow
export MEDIFLOW_DB_PASSWORD=...   # scratch instance only
export MEDIFLOW_RESTORE_DB=mediflow_restore_drill
./infra/backup/restore.sh
```

4. Confirm the printed row counts are non-zero and match the source (spot-check `audit_event` count).
5. Spot-check MinIO mirror: `mc ls mfbackup/mediflow-backups/objects/<stamp>/ | head`.
6. `DROP DATABASE mediflow_restore_drill;` and shred local decrypted files.
7. Record date, operator, dump stamp, and pass/fail in the ops runbook.

## Full site recovery (high level)

1. Bring up `infra/docker-compose.prod.yml` with **new** empty volumes.
2. Restore Postgres into the primary DB **before** starting `backend` (or stop backend first).
3. `mc mirror` the chosen `objects/<stamp>/` back to `MEDIFLOW_MINIO_BUCKET`.
4. Restore Docker secrets (`MEDIFLOW_FIELD_ENCRYPTION_KEY`, MinIO KMS key) from offline escrow — without them encrypted columns/objects stay opaque.
5. Start backend; run smoke login + one read of a known patient chart; verify audit chain gauge is healthy in Grafana.
