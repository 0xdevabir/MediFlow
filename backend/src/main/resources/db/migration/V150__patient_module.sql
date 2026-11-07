-- T02 patient module (M1). Range V150-V199.

-- Self-reported vs clinician-verified allergies (DOCX Table 17 footnote 2).
-- Rows written by other modules through the shared Allergy entity default to unverified.
ALTER TABLE allergy
    ADD COLUMN verified     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN source       VARCHAR(32) NOT NULL DEFAULT 'SELF_REPORTED',
    ADD COLUMN reported_by  UUID REFERENCES app_user(id),
    ADD COLUMN verified_by  UUID REFERENCES app_user(id),
    ADD COLUMN verified_at  TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS ix_allergy_patient_id ON allergy (patient_id);

-- Registration metadata, verification state and blind indexes for duplicate detection.
-- national_id / phone are AES-GCM with random IVs, so equality lookups use HMAC blind indexes.
CREATE TABLE patient_profile (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id           UUID NOT NULL UNIQUE REFERENCES patient(id),
    registration_source  VARCHAR(32) NOT NULL,
    registered_by        UUID REFERENCES app_user(id),
    national_id_hash     VARCHAR(64),
    identity_hash        VARCHAR(64),
    phone_verified_at    TIMESTAMPTZ,
    email_verified_at    TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_patient_profile_national_id_hash
    ON patient_profile (national_id_hash) WHERE national_id_hash IS NOT NULL;
CREATE INDEX ix_patient_profile_identity_hash ON patient_profile (identity_hash);

-- Per-year MRN counter: MF-YYYY-NNNNNN.
CREATE TABLE patient_mrn_counter (
    mrn_year    INT PRIMARY KEY,
    last_value  BIGINT NOT NULL
);

-- One-time codes for phone / email verification (only a keyed hash of the code is stored).
CREATE TABLE patient_contact_verification (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id   UUID NOT NULL REFERENCES patient(id),
    channel      VARCHAR(16) NOT NULL,
    code_hash    VARCHAR(64) NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    attempts     INT NOT NULL DEFAULT 0,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_patient_contact_verification_patient ON patient_contact_verification (patient_id, channel);

-- Self-reported medical history (unverified until a doctor confirms).
CREATE TABLE patient_history_item (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id   UUID NOT NULL REFERENCES patient(id),
    category     VARCHAR(32) NOT NULL,
    description  TEXT NOT NULL, -- AES-GCM ciphertext
    since_text   VARCHAR(100),
    verified     BOOLEAN NOT NULL DEFAULT FALSE,
    source       VARCHAR(32) NOT NULL,
    reported_by  UUID REFERENCES app_user(id),
    verified_by  UUID REFERENCES app_user(id),
    verified_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_patient_history_item_patient ON patient_history_item (patient_id);

-- Name / date-of-birth changes requested by the patient, decided by front desk (Table 17 footnote 1).
CREATE TABLE patient_demographic_change (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id         UUID NOT NULL REFERENCES patient(id),
    requested_by       UUID REFERENCES app_user(id),
    new_given_name     VARCHAR(100),
    new_family_name    VARCHAR(100),
    new_date_of_birth  DATE,
    reason             TEXT,
    status             VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewed_by        UUID REFERENCES app_user(id),
    reviewed_at        TIMESTAMPTZ,
    review_note        TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_patient_demographic_change_status ON patient_demographic_change (status);

-- Uploaded identity documents (object bytes live in MinIO via ObjectStoragePort).
CREATE TABLE patient_document (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id         UUID NOT NULL REFERENCES patient(id),
    document_type      VARCHAR(32) NOT NULL,
    object_key         VARCHAR(300) NOT NULL UNIQUE,
    content_type       VARCHAR(100) NOT NULL,
    size_bytes         BIGINT NOT NULL,
    original_filename  VARCHAR(255),
    uploaded_by        UUID REFERENCES app_user(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_patient_document_patient ON patient_document (patient_id);
