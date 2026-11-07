-- T01 identity: login lock-out state, password reset tokens, break-glass review, referral sharing.

CREATE TABLE identity_login_state (
    user_id             UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    failed_attempts     INT NOT NULL DEFAULT 0,
    last_failed_at      TIMESTAMPTZ,
    locked_until        TIMESTAMPTZ,
    mfa_last_used_step  BIGINT,
    password_changed_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE identity_password_reset_token (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE, -- SHA-256 hex; raw token only ever in the e-mail
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_pwd_reset_user ON identity_password_reset_token (user_id);

CREATE TABLE identity_break_glass_review (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    break_glass_id  UUID NOT NULL UNIQUE REFERENCES break_glass_access(id),
    reviewed_by     UUID NOT NULL REFERENCES app_user(id),
    outcome         VARCHAR(32) NOT NULL, -- JUSTIFIED | NOT_JUSTIFIED
    note            TEXT,
    reviewed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_break_glass_user_patient ON break_glass_access (user_id, patient_id, expires_at);

-- Time-limited "share with another doctor" (referral access), always backed by a patient consent row.
CREATE TABLE identity_referral_share (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    grantee_user_id UUID NOT NULL REFERENCES app_user(id),
    granted_by      UUID NOT NULL REFERENCES app_user(id),
    consent_id      UUID NOT NULL REFERENCES consent(id),
    reason          TEXT,
    starts_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CHECK (expires_at > starts_at)
);
CREATE INDEX idx_referral_share_grantee ON identity_referral_share (grantee_user_id, patient_id, expires_at);
