-- T10 Notifications & reminders (M8). Companion tables for the T00 `notification` / `reminder` tables.

-- Delivery metadata for one row of `notification` (event, priority, retries, escalation, read state).
CREATE TABLE notification_delivery (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id      UUID NOT NULL UNIQUE REFERENCES notification(id) ON DELETE CASCADE,
    root_notification_id UUID NOT NULL REFERENCES notification(id) ON DELETE CASCADE,
    event_type           VARCHAR(64) NOT NULL,
    priority             VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    attempts             INT NOT NULL DEFAULT 0,
    last_error           VARCHAR(500),
    escalated_to_id      UUID REFERENCES notification(id),
    read_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_notification_delivery_root ON notification_delivery(root_notification_id);
CREATE INDEX ix_notification_recipient_channel ON notification(recipient_user_id, channel, created_at DESC);

-- Per-user notification preferences (channels, quiet hours, language).
CREATE TABLE notification_preference (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    sms_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_start     TIME,
    quiet_end       TIME,
    locale          VARCHAR(10),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- Web-push (FCM) registration tokens.
CREATE TABLE push_device_token (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token           VARCHAR(512) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (user_id, token)
);

-- Risk R7: undeliverable notifications land on a staff phone-call list.
CREATE TABLE staff_call_entry (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    root_notification_id UUID REFERENCES notification(id) ON DELETE SET NULL,
    patient_id           UUID REFERENCES patient(id),
    recipient_user_id    UUID REFERENCES app_user(id),
    event_type           VARCHAR(64) NOT NULL,
    priority             VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    reason               VARCHAR(500) NOT NULL,
    message              TEXT,
    status               VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    resolved_by          UUID REFERENCES app_user(id),
    resolved_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_staff_call_entry_status ON staff_call_entry(status, created_at);

-- Patient acknowledgement of a reminder (medicine adherence log).
CREATE TABLE adherence_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reminder_id     UUID NOT NULL UNIQUE REFERENCES reminder(id) ON DELETE CASCADE,
    patient_id      UUID NOT NULL REFERENCES patient(id),
    outcome         VARCHAR(16) NOT NULL, -- TAKEN | SKIPPED
    acknowledged_at TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- Reminder lookups: due scan and per-source cancellation.
CREATE INDEX ix_reminder_due ON reminder(status, scheduled_at);
CREATE INDEX ix_reminder_patient ON reminder(patient_id, scheduled_at);
CREATE INDEX ix_reminder_source ON reminder((payload ->> 'sourceId'));
