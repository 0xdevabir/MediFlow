-- T04 (M2): booking metadata, queue ordering metadata, triage history, override log,
-- Red Alert / ER extensions. Shared V1 tables (appointment, queue_entry, triage, red_alert)
-- stay the source of truth; these tables carry the extra state T04 needs.

-- ---------------------------------------------------------------------------
-- Booking
-- ---------------------------------------------------------------------------
CREATE TABLE appointment_booking_meta (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id       UUID NOT NULL UNIQUE REFERENCES appointment(id),
    booked_by            UUID REFERENCES app_user(id),
    checkin_code         VARCHAR(16) NOT NULL UNIQUE,
    emergency_flag       BOOLEAN NOT NULL DEFAULT FALSE,
    emergency_flagged_by UUID REFERENCES app_user(id),
    emergency_flagged_at TIMESTAMPTZ,
    emergency_note       TEXT,
    rescheduled_from     UUID REFERENCES appointment(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0
);

-- Same patient cannot hold two live bookings on one slot (double-click / replay).
CREATE UNIQUE INDEX ux_appt_patient_slot_active
    ON appointment (patient_id, slot_id)
    WHERE slot_id IS NOT NULL AND status <> 'CANCELLED';

CREATE INDEX ix_appt_patient_booked ON appointment (patient_id, booked_at DESC);
CREATE INDEX ix_appt_slot ON appointment (slot_id);

-- Defence in depth for double-booking: the optimistic-locked counter can never exceed capacity.
ALTER TABLE slot
    ADD CONSTRAINT ck_appt_slot_booked_within_capacity
    CHECK (booked_count >= 0 AND booked_count <= capacity);

-- ---------------------------------------------------------------------------
-- Queue
-- ---------------------------------------------------------------------------
CREATE TABLE appointment_token_counter (
    doctor_id  UUID NOT NULL REFERENCES practitioner(id),
    queue_date DATE NOT NULL,
    last_seq   INT NOT NULL,
    PRIMARY KEY (doctor_id, queue_date)
);

CREATE TABLE appointment_queue_meta (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_entry_id  UUID NOT NULL UNIQUE REFERENCES queue_entry(id),
    appointment_id  UUID NOT NULL UNIQUE REFERENCES appointment(id),
    doctor_id       UUID NOT NULL REFERENCES practitioner(id),
    queue_date      DATE NOT NULL,
    token_seq       INT NOT NULL,
    arrived_at      TIMESTAMPTZ NOT NULL,
    slot_starts_at  TIMESTAMPTZ,
    vitals_level    VARCHAR(32),
    override_bucket INT,
    override_sub    INT,
    override_time   TIMESTAMPTZ,
    removed_reason  VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (doctor_id, queue_date, token_seq)
);

CREATE INDEX ix_appt_queue_meta_doctor_date ON appointment_queue_meta (doctor_id, queue_date);

-- Every nurse re-order is kept here (in addition to AuditPort). Append-only.
CREATE TABLE appointment_queue_override (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_entry_id  UUID NOT NULL REFERENCES queue_entry(id),
    appointment_id  UUID NOT NULL REFERENCES appointment(id),
    actor_user_id   UUID NOT NULL REFERENCES app_user(id),
    action          VARCHAR(32) NOT NULL,
    from_position   INT,
    to_position     INT,
    reason          TEXT NOT NULL CHECK (length(btrim(reason)) > 0),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_appt_queue_override_entry ON appointment_queue_override (queue_entry_id);

-- ---------------------------------------------------------------------------
-- Triage history (who set which level, from which source). Append-only.
-- ---------------------------------------------------------------------------
CREATE TABLE appointment_triage_history (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id   UUID NOT NULL REFERENCES appointment(id),
    level            VARCHAR(32) NOT NULL,
    symptom_severity VARCHAR(32),
    source           VARCHAR(32) NOT NULL,
    set_by           UUID REFERENCES app_user(id),
    note             TEXT,
    set_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_appt_triage_history_appt ON appointment_triage_history (appointment_id);

CREATE OR REPLACE FUNCTION appt_forbid_log_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % not allowed', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_appt_queue_override_no_update
    BEFORE UPDATE OR DELETE ON appointment_queue_override
    FOR EACH ROW EXECUTE FUNCTION appt_forbid_log_mutation();

CREATE TRIGGER trg_appt_triage_history_no_update
    BEFORE UPDATE OR DELETE ON appointment_triage_history
    FOR EACH ROW EXECUTE FUNCTION appt_forbid_log_mutation();

-- ---------------------------------------------------------------------------
-- Red Alert / ER (requested by T01: assigned ER user for scoped access)
-- ---------------------------------------------------------------------------
ALTER TABLE red_alert
    ADD COLUMN assigned_er_user_id UUID REFERENCES app_user(id),
    ADD COLUMN acknowledged_by     UUID REFERENCES app_user(id),
    ADD COLUMN source              VARCHAR(32),
    ADD COLUMN handover_by         UUID REFERENCES app_user(id),
    ADD COLUMN handover_at         TIMESTAMPTZ,
    ADD COLUMN handover_note       TEXT;

CREATE INDEX ix_red_alert_active ON red_alert (hospital_id, raised_at) WHERE handover_status IS NULL;
CREATE INDEX ix_red_alert_patient_active ON red_alert (patient_id) WHERE handover_status IS NULL;
