-- MediFlow AI baseline schema (T00). Module owners add V100+ in their ranges.
-- UUID PKs; timestamps timestamptz; soft clinical immutability enforced in app layers.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- Identity / access
-- ---------------------------------------------------------------------------
CREATE TABLE app_user (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(200) NOT NULL,
    email_enc       TEXT,
    phone_enc       TEXT,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    mfa_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    locale          VARCHAR(10) NOT NULL DEFAULT 'en',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE user_role (
    user_id         UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role            VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE mfa_secret (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    secret_enc      TEXT NOT NULL,
    verified        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE consent (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id      UUID, -- FK added after patient
    user_id         UUID REFERENCES app_user(id),
    consent_type    VARCHAR(64) NOT NULL,
    granted         BOOLEAN NOT NULL,
    version_label   VARCHAR(64) NOT NULL,
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE break_glass_access (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES app_user(id),
    patient_id      UUID NOT NULL, -- FK after patient
    reason          TEXT NOT NULL,
    approved_by     UUID REFERENCES app_user(id),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- Directory (D6)
-- ---------------------------------------------------------------------------
CREATE TABLE hospital (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,
    type            VARCHAR(64),
    address         TEXT,
    phone           VARCHAR(64),
    emergency_phone VARCHAR(64),
    opening_hours   TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE department (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL REFERENCES hospital(id),
    name            VARCHAR(200) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, name)
);

CREATE TABLE practitioner (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES app_user(id),
    name            VARCHAR(200) NOT NULL,
    role_label      VARCHAR(64),
    licence_no      VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE practitioner_role (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    practitioner_id UUID NOT NULL REFERENCES practitioner(id),
    hospital_id     UUID NOT NULL REFERENCES hospital(id),
    department_id   UUID NOT NULL REFERENCES department(id),
    schedule        TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE slot (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    practitioner_role_id UUID NOT NULL REFERENCES practitioner_role(id),
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    capacity        INT NOT NULL DEFAULT 1,
    booked_count    INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- Patient (M1)
-- ---------------------------------------------------------------------------
CREATE TABLE patient (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID UNIQUE REFERENCES app_user(id),
    mrn             VARCHAR(64) UNIQUE,
    given_name      VARCHAR(100) NOT NULL,
    family_name     VARCHAR(100) NOT NULL,
    date_of_birth   DATE,
    sex             VARCHAR(32),
    national_id     TEXT, -- AES-GCM ciphertext
    phone           TEXT, -- AES-GCM ciphertext
    email           TEXT, -- AES-GCM ciphertext
    address         TEXT,
    pregnancy_status VARCHAR(32),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE consent
    ADD CONSTRAINT fk_consent_patient FOREIGN KEY (patient_id) REFERENCES patient(id);

ALTER TABLE break_glass_access
    ADD CONSTRAINT fk_break_glass_patient FOREIGN KEY (patient_id) REFERENCES patient(id);

CREATE TABLE allergy (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    substance       VARCHAR(200) NOT NULL,
    substance_code  VARCHAR(64),
    criticality     VARCHAR(32),
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- Appointments / queue / triage / red alert (M2)
-- ---------------------------------------------------------------------------
CREATE TABLE appointment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    doctor_id       UUID REFERENCES practitioner(id),
    hospital_id     UUID NOT NULL REFERENCES hospital(id),
    slot_id         UUID REFERENCES slot(id),
    status          VARCHAR(32) NOT NULL DEFAULT 'BOOKED',
    priority_level  VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    emergency_level VARCHAR(32),
    booked_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    checked_in_at   TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE queue_entry (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id  UUID NOT NULL UNIQUE REFERENCES appointment(id),
    hospital_id     UUID NOT NULL REFERENCES hospital(id),
    token_no        VARCHAR(32) NOT NULL,
    position        INT NOT NULL,
    priority_level  VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    status          VARCHAR(32) NOT NULL DEFAULT 'WAITING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE triage (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id  UUID NOT NULL UNIQUE REFERENCES appointment(id),
    level           VARCHAR(32) NOT NULL,
    set_by          UUID REFERENCES app_user(id),
    note            TEXT,
    set_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE encounter (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    doctor_id       UUID REFERENCES practitioner(id),
    hospital_id     UUID NOT NULL REFERENCES hospital(id),
    appointment_id  UUID UNIQUE REFERENCES appointment(id),
    status          VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    diagnosis_summary TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE red_alert (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id    UUID REFERENCES encounter(id),
    appointment_id  UUID REFERENCES appointment(id),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    hospital_id     UUID NOT NULL REFERENCES hospital(id),
    level           VARCHAR(32) NOT NULL DEFAULT 'CRITICAL',
    raised_by       UUID REFERENCES app_user(id),
    reason          TEXT,
    raised_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMPTZ,
    handover_status VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- Clinical / vitals (M3)
-- ---------------------------------------------------------------------------
CREATE TABLE vital_rule (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vital_type      VARCHAR(64) NOT NULL,
    population      VARCHAR(64) NOT NULL DEFAULT 'ADULT',
    unit            VARCHAR(32) NOT NULL,
    normal_min      NUMERIC(12, 4),
    normal_max      NUMERIC(12, 4),
    borderline_min  NUMERIC(12, 4),
    borderline_max  NUMERIC(12, 4),
    critical_min    NUMERIC(12, 4),
    critical_max    NUMERIC(12, 4),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (vital_type, population)
);

CREATE TABLE vital_observation (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id    UUID NOT NULL REFERENCES encounter(id),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    vital_type      VARCHAR(64) NOT NULL,
    value_numeric   NUMERIC(12, 4),
    value_text      VARCHAR(64),
    unit            VARCHAR(32),
    classification  VARCHAR(32) NOT NULL,
    recorded_by     UUID REFERENCES app_user(id),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE condition_dx (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id    UUID NOT NULL REFERENCES encounter(id),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    icd10_code      VARCHAR(16) NOT NULL,
    display         VARCHAR(255) NOT NULL,
    clinical_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    recorded_by     UUID REFERENCES app_user(id),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE clinical_note (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id    UUID NOT NULL REFERENCES encounter(id),
    note_version    INT NOT NULL DEFAULT 1,
    content         TEXT NOT NULL,
    author_id       UUID REFERENCES app_user(id),
    supersedes_id   UUID REFERENCES clinical_note(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (encounter_id, note_version)
);

-- ---------------------------------------------------------------------------
-- Lab (M5)
-- ---------------------------------------------------------------------------
CREATE TABLE lab_test (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    loinc_code      VARCHAR(64),
    direct_allowed  BOOLEAN NOT NULL DEFAULT FALSE,
    price           NUMERIC(12, 2),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE lab_package (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID REFERENCES hospital(id),
    name            VARCHAR(200) NOT NULL,
    price           NUMERIC(12, 2),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE lab_package_item (
    package_id      UUID NOT NULL REFERENCES lab_package(id) ON DELETE CASCADE,
    test_id         UUID NOT NULL REFERENCES lab_test(id),
    PRIMARY KEY (package_id, test_id)
);

CREATE TABLE lab_order (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    hospital_id     UUID NOT NULL REFERENCES hospital(id),
    encounter_id    UUID REFERENCES encounter(id), -- null for DIRECT
    test_id         UUID REFERENCES lab_test(id),
    package_id      UUID REFERENCES lab_package(id),
    order_type      VARCHAR(32) NOT NULL, -- DOCTOR | DIRECT
    status          VARCHAR(32) NOT NULL DEFAULT 'ORDERED',
    ordered_by      UUID REFERENCES app_user(id),
    ordered_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE sample (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id    UUID NOT NULL REFERENCES lab_order(id),
    barcode         VARCHAR(64),
    status          VARCHAR(32) NOT NULL DEFAULT 'COLLECTED',
    collected_at    TIMESTAMPTZ,
    received_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE diagnostic_report (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id    UUID NOT NULL UNIQUE REFERENCES lab_order(id),
    file_ref        VARCHAR(512),
    verified_by     UUID REFERENCES app_user(id),
    verified_at     TIMESTAMPTZ,
    abnormal_flag   BOOLEAN NOT NULL DEFAULT FALSE,
    critical_flag   BOOLEAN NOT NULL DEFAULT FALSE,
    summary         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- Pharmacy / DGDA dictionary (M6)
-- ---------------------------------------------------------------------------
CREATE TABLE drug_generic (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL UNIQUE,
    atc_code        VARCHAR(32),
    rxnorm_code     VARCHAR(32),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE drug_brand (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,
    dgda_reg_no     VARCHAR(64),
    strength        VARCHAR(64),
    form            VARCHAR(64),
    generic_id      UUID NOT NULL REFERENCES drug_generic(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_drug_brand_dgda ON drug_brand (dgda_reg_no) WHERE dgda_reg_no IS NOT NULL;

CREATE TABLE drug_interaction (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    generic_a_id    UUID NOT NULL REFERENCES drug_generic(id),
    generic_b_id    UUID NOT NULL REFERENCES drug_generic(id),
    severity        VARCHAR(32) NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CHECK (generic_a_id <> generic_b_id)
);

CREATE TABLE drug_dose_limit (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    generic_id      UUID NOT NULL REFERENCES drug_generic(id),
    population      VARCHAR(64) NOT NULL DEFAULT 'ADULT',
    route           VARCHAR(32),
    min_dose        NUMERIC(12, 4),
    max_dose        NUMERIC(12, 4),
    unit            VARCHAR(32),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE pharmacy_stock (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL REFERENCES hospital(id),
    brand_id        UUID NOT NULL REFERENCES drug_brand(id),
    quantity        INT NOT NULL DEFAULT 0,
    reorder_level   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, brand_id)
);

-- ---------------------------------------------------------------------------
-- Prescription + AI (M4)
-- ---------------------------------------------------------------------------
CREATE TABLE prescription (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id    UUID NOT NULL REFERENCES encounter(id),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    status          VARCHAR(32) NOT NULL DEFAULT 'DRAFT', -- DRAFT|SIGNED|AMENDED|ENTERED_IN_ERROR
    signed_at       TIMESTAMPTZ,
    signed_by       UUID REFERENCES app_user(id),
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE prescription_item (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id UUID NOT NULL REFERENCES prescription(id) ON DELETE CASCADE,
    brand_id        UUID REFERENCES drug_brand(id),
    generic_id      UUID NOT NULL REFERENCES drug_generic(id),
    dose            VARCHAR(64),
    route           VARCHAR(64),
    frequency       VARCHAR(64),
    duration        VARCHAR(64),
    confidence      NUMERIC(5, 4),
    review_status   VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    conflict_severity VARCHAR(32),
    raw_text        TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE ai_job (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID NOT NULL UNIQUE,
    encounter_id    UUID NOT NULL REFERENCES encounter(id),
    prescription_id UUID REFERENCES prescription(id),
    input_type      VARCHAR(16) NOT NULL, -- IMAGE|TEXT
    object_key      VARCHAR(512),
    text_input      TEXT,
    locale          VARCHAR(10) NOT NULL DEFAULT 'en',
    status          VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    result_json     JSONB,
    model_version   VARCHAR(64),
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE ai_flag_override (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_item_id UUID NOT NULL REFERENCES prescription_item(id),
    overridden_by   UUID NOT NULL REFERENCES app_user(id),
    reason          TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE dispense (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id UUID NOT NULL REFERENCES prescription(id),
    prescription_item_id UUID REFERENCES prescription_item(id),
    pharmacist_id   UUID REFERENCES app_user(id),
    hospital_id     UUID REFERENCES hospital(id),
    quantity        INT,
    qr_token        VARCHAR(128),
    dispensed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- Notifications (M8)
-- ---------------------------------------------------------------------------
CREATE TABLE reminder (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    reminder_type   VARCHAR(64) NOT NULL,
    channel         VARCHAR(32) NOT NULL,
    scheduled_at    TIMESTAMPTZ NOT NULL,
    payload         JSONB,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE notification (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id UUID REFERENCES app_user(id),
    patient_id      UUID REFERENCES patient(id),
    channel         VARCHAR(32) NOT NULL,
    locale          VARCHAR(10) NOT NULL DEFAULT 'en',
    subject         VARCHAR(255),
    body            TEXT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- Audit (M9) — append-only, hash-chained
-- ---------------------------------------------------------------------------
CREATE TABLE audit_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_user_id   UUID,
    patient_id      UUID,
    action          VARCHAR(64) NOT NULL,
    resource_type   VARCHAR(64),
    resource_id     UUID,
    details         JSONB,
    prev_hash       VARCHAR(128),
    hash            VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_event_patient ON audit_event (patient_id, occurred_at DESC);
CREATE INDEX idx_audit_event_actor ON audit_event (actor_user_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION forbid_audit_event_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only: % not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_event_no_update
    BEFORE UPDATE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION forbid_audit_event_mutation();

CREATE TRIGGER trg_audit_event_no_delete
    BEFORE DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION forbid_audit_event_mutation();

-- Prefer least-privilege: if role mediflow exists, revoke mutations (dev may use owner).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mediflow') THEN
        REVOKE UPDATE, DELETE ON audit_event FROM mediflow;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Admin settings (M10)
-- ---------------------------------------------------------------------------
CREATE TABLE system_setting (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    setting_key     VARCHAR(128) NOT NULL,
    setting_value   TEXT NOT NULL,
    value_version   INT NOT NULL DEFAULT 1,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (setting_key, value_version)
);

CREATE TABLE setting_change_request (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    setting_key     VARCHAR(128) NOT NULL,
    proposed_value  TEXT NOT NULL,
    requested_by    UUID NOT NULL REFERENCES app_user(id),
    approved_by     UUID REFERENCES app_user(id),
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- Seed confidence threshold (raise-only thereafter; dual approval in T11)
INSERT INTO system_setting (setting_key, setting_value, value_version, description)
VALUES ('ai.confidence.threshold', '0.85', 1, 'Per-item confidence gate; may only be raised with dual approval');
