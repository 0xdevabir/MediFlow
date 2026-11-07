-- T11 (M9 audit): hash-chain ordering, integrity checks, anomaly alerts, audit settings.
--
-- audit_event stays append-only (V1 trigger). Adding an identity column rewrites the table but
-- fires no row triggers, so existing rows simply get a sequence number in physical order.

ALTER TABLE audit_event ADD COLUMN seq BIGINT GENERATED ALWAYS AS IDENTITY;
CREATE UNIQUE INDEX ux_audit_event_seq ON audit_event (seq);
CREATE INDEX idx_audit_event_occurred ON audit_event (occurred_at DESC);
CREATE INDEX idx_audit_event_action ON audit_event (action, occurred_at DESC);

-- Result of each chain verification run ("chain integrity OK / broken at #n").
CREATE TABLE audit_chain_check (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checked_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ok              BOOLEAN NOT NULL,
    rows_checked    BIGINT NOT NULL,
    broken_count    BIGINT NOT NULL DEFAULT 0,
    first_broken_seq BIGINT,
    broken_seqs     TEXT,          -- first few broken sequence numbers, comma separated
    head_seq        BIGINT,        -- last row seen; next run checks it still exists unchanged
    head_hash       VARCHAR(128),
    message         TEXT,
    triggered_by    UUID REFERENCES app_user(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_chain_check_at ON audit_chain_check (checked_at DESC);

-- Anomaly alerts, e.g. one user reading more than N distinct patients' records in an hour.
CREATE TABLE audit_anomaly (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id   UUID NOT NULL REFERENCES app_user(id),
    kind            VARCHAR(64) NOT NULL,
    window_start    TIMESTAMPTZ NOT NULL,
    window_end      TIMESTAMPTZ NOT NULL,
    observed_count  INT NOT NULL,
    threshold       INT NOT NULL,
    reviewed_by     UUID REFERENCES app_user(id),
    reviewed_at     TIMESTAMPTZ,
    review_note     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (actor_user_id, kind, window_start)
);

-- Non-clinical audit settings (admin may change directly; versioned + audited).
INSERT INTO system_setting (setting_key, setting_value, value_version, description)
VALUES ('audit.retention.years', '10', 1, 'Years audit events are kept before they become eligible for archive export'),
       ('audit.anomaly.patients-per-hour', '50', 1, 'Alert when one user touches more distinct patient records than this within one hour');
