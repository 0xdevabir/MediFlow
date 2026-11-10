-- LabBadgeEntity extends BaseEntity (updated_at + @Version), but V300 created clinical_lab_badge
-- without those columns, so ddl-auto=validate failed at startup.
ALTER TABLE clinical_lab_badge
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS version    BIGINT      NOT NULL DEFAULT 0;
