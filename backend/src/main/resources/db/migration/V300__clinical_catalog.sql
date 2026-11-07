-- T05 clinical catalog: vital definitions (data-driven types), allergy confirmations,
-- ICD-10 subset, and per-doctor lab-report badge state.

CREATE TABLE clinical_vital_definition (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(64) NOT NULL UNIQUE,
    kind            VARCHAR(32) NOT NULL,
    ucum_unit       VARCHAR(32) NOT NULL,
    loinc_code      VARCHAR(64),
    display_en      VARCHAR(200) NOT NULL,
    display_bn      VARCHAR(200),
    plausible_min   NUMERIC(12, 4),
    plausible_max   NUMERIC(12, 4),
    sort_order      INT NOT NULL DEFAULT 0,
    nurse_entry     BOOLEAN NOT NULL DEFAULT TRUE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- Allergy has no verified column in V1; confirmations live here (owns T05).
CREATE TABLE clinical_allergy_confirm (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    allergy_id      UUID NOT NULL UNIQUE REFERENCES allergy(id),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    confirmed_by    UUID NOT NULL REFERENCES app_user(id),
    confirmed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE clinical_icd10 (
    code            VARCHAR(16) PRIMARY KEY,
    display_en      VARCHAR(255) NOT NULL,
    display_bn      VARCHAR(255),
    chapter         VARCHAR(64),
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX clinical_icd10_display_en_idx ON clinical_icd10 (lower(display_en));

-- Unread LabReportVerified badges for the assigned doctor (patient-scoped).
CREATE TABLE clinical_lab_badge (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_user_id  UUID NOT NULL REFERENCES app_user(id),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    report_id       UUID NOT NULL,
    lab_order_id    UUID NOT NULL,
    abnormal        BOOLEAN NOT NULL DEFAULT FALSE,
    critical        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at         TIMESTAMPTZ,
    UNIQUE (doctor_user_id, report_id)
);

CREATE INDEX clinical_lab_badge_unread_idx
    ON clinical_lab_badge (doctor_user_id, created_at DESC)
    WHERE read_at IS NULL;
