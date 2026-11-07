-- T08 Pharmacy & DGDA drug dictionary (M6) — module-owned schema (range V450–V499).
-- Shared tables (drug_generic, drug_brand, drug_interaction, drug_dose_limit, pharmacy_stock,
-- dispense) come from V1 baseline; this migration only adds indexes and module-owned tables.

-- ---------------------------------------------------------------------------
-- pg_trgm for fuzzy dictionary lookup. pg_trgm is a "trusted" extension (PG13+), so a database
-- owner can install it without superuser. If it still cannot be installed, the migration does not
-- fail: the Java side falls back to in-memory fuzzy matching (DrugDictionaryService).
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_trgm not available (%); fuzzy lookup will use the Java fallback', SQLERRM;
END
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS ix_drug_brand_name_trgm ON drug_brand USING gin (lower(name) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS ix_drug_generic_name_trgm ON drug_generic USING gin (lower(name) gin_trgm_ops)';
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS ix_drug_brand_name_lower ON drug_brand (lower(name));
CREATE INDEX IF NOT EXISTS ix_drug_generic_name_lower ON drug_generic (lower(name));
CREATE INDEX IF NOT EXISTS ix_dispense_prescription ON dispense (prescription_id);

-- ---------------------------------------------------------------------------
-- Versioned DGDA register imports (admin CSV upload) with a report of unmapped rows.
-- ---------------------------------------------------------------------------
CREATE TABLE drug_dictionary_import (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_version  INT NOT NULL UNIQUE,
    source_label    VARCHAR(200) NOT NULL,
    file_name       VARCHAR(255),
    imported_by     UUID REFERENCES app_user(id),
    total_rows      INT NOT NULL DEFAULT 0,
    created_generics INT NOT NULL DEFAULT 0,
    created_brands  INT NOT NULL DEFAULT 0,
    updated_brands  INT NOT NULL DEFAULT 0,
    unmapped_rows   INT NOT NULL DEFAULT 0,
    unmapped_report TEXT,          -- one line per unmapped row: "<line>: <reason> | <raw row>"
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- Where each dictionary / reference row came from. Seeded rows are ILLUSTRATIVE_SEED (sample BD
-- brands) or PLACEHOLDER_SEED (interaction / dose data: "placeholder — needs licensed source").
CREATE TABLE drug_data_provenance (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    table_name      VARCHAR(64) NOT NULL,   -- drug_generic | drug_brand | drug_interaction | drug_dose_limit
    row_id          UUID NOT NULL,
    source          VARCHAR(32) NOT NULL,   -- ILLUSTRATIVE_SEED | PLACEHOLDER_SEED | DGDA_IMPORT
    import_id       UUID REFERENCES drug_dictionary_import(id),
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (table_name, row_id)
);

-- ---------------------------------------------------------------------------
-- Stock batches (batch number + expiry) under the shared per-pharmacy pharmacy_stock row.
-- pharmacy_stock.quantity is kept equal to the sum of its batches.
-- ---------------------------------------------------------------------------
CREATE TABLE pharmacy_stock_batch (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_id        UUID NOT NULL REFERENCES pharmacy_stock(id),
    batch_no        VARCHAR(64) NOT NULL,
    expiry_date     DATE NOT NULL,
    quantity        INT NOT NULL CHECK (quantity >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (stock_id, batch_no)
);

-- Batch / expiry / brand actually handed out for one shared dispense row.
CREATE TABLE dispense_batch (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispense_id     UUID NOT NULL UNIQUE REFERENCES dispense(id),
    batch_id        UUID NOT NULL REFERENCES pharmacy_stock_batch(id),
    brand_id        UUID NOT NULL REFERENCES drug_brand(id),
    batch_no        VARCHAR(64) NOT NULL,
    expiry_date     DATE NOT NULL,
    substitution_request_id UUID,
    identity_method VARCHAR(16) NOT NULL,   -- QR | TWO_IDENTIFIERS
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- Pharmacist → doctor requests: send back (any doubt), substitution (out of stock), referral
-- to another pharmacy.
-- ---------------------------------------------------------------------------
CREATE TABLE pharmacy_request (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind            VARCHAR(32) NOT NULL,   -- SEND_BACK | SUBSTITUTION | REFERRED_ELSEWHERE
    status          VARCHAR(32) NOT NULL,   -- OPEN | RESOLVED | PENDING | APPROVED | REJECTED | CLOSED | CANCELLED
    prescription_id UUID NOT NULL REFERENCES prescription(id),
    prescription_item_id UUID REFERENCES prescription_item(id),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    hospital_id     UUID REFERENCES hospital(id),
    proposed_brand_id UUID REFERENCES drug_brand(id),
    reason          TEXT NOT NULL,
    requested_by    UUID NOT NULL REFERENCES app_user(id),
    doctor_user_id  UUID REFERENCES app_user(id),
    decided_by      UUID REFERENCES app_user(id),
    decided_at      TIMESTAMPTZ,
    decision_note   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_pharmacy_request_rx ON pharmacy_request (prescription_id);
CREATE INDEX ix_pharmacy_request_doctor ON pharmacy_request (doctor_user_id, status);

-- PrescriptionRevoked received → block dispensing (defence in depth on top of prescription.status).
CREATE TABLE pharmacy_rx_block (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id UUID NOT NULL UNIQUE REFERENCES prescription(id),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    revoked_at      TIMESTAMPTZ NOT NULL,
    already_dispensed BOOLEAN NOT NULL DEFAULT FALSE,
    alert_acknowledged_by UUID REFERENCES app_user(id),
    alert_acknowledged_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);
