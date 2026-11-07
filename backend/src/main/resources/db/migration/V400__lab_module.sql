-- T07 Lab (M5): tables the lab module adds on top of the V1 baseline
-- (lab_test, lab_package, lab_package_item, lab_order, sample, diagnostic_report).

-- Per-order workflow details that the baseline lab_order row does not carry.
CREATE TABLE lab_order_detail (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id    UUID NOT NULL UNIQUE REFERENCES lab_order(id) ON DELETE CASCADE,
    booking_ref     VARCHAR(32) NOT NULL,          -- shared by all orders of one booking
    booked_by       UUID REFERENCES app_user(id),  -- patient / front desk / doctor user
    booked_via      VARCHAR(32) NOT NULL,          -- PATIENT | FRONT_DESK | DOCTOR
    scheduled_date  DATE,
    slot_label      VARCHAR(32),
    price           NUMERIC(12, 2),
    qr_token        VARCHAR(64) NOT NULL,          -- identity check at sample collection
    assigned_tech_id UUID REFERENCES app_user(id), -- lab tech who collected the sample
    resulted_by     UUID REFERENCES app_user(id),
    resulted_at     TIMESTAMPTZ,
    rejected_reason TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_lab_order_detail_booking ON lab_order_detail (booking_ref);
CREATE INDEX ix_lab_order_detail_tech ON lab_order_detail (assigned_tech_id);

-- Analytes measured by a test, with reference / critical ranges (LOINC + UCUM).
CREATE TABLE lab_reference_range (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id         UUID NOT NULL REFERENCES lab_test(id) ON DELETE CASCADE,
    analyte_code    VARCHAR(64) NOT NULL,
    analyte_name    VARCHAR(200) NOT NULL,
    loinc_code      VARCHAR(64),
    ucum_unit       VARCHAR(64),
    sex             VARCHAR(1),                    -- NULL = any, M, F
    ref_low         NUMERIC(14, 4),
    ref_high        NUMERIC(14, 4),
    critical_low    NUMERIC(14, 4),
    critical_high   NUMERIC(14, 4),
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lab_range UNIQUE (test_id, analyte_code, sex)
);

-- Structured result values of a diagnostic report, with the range used and the computed flag.
CREATE TABLE lab_result_value (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id       UUID NOT NULL REFERENCES diagnostic_report(id) ON DELETE CASCADE,
    test_id         UUID REFERENCES lab_test(id),
    analyte_code    VARCHAR(64) NOT NULL,
    analyte_name    VARCHAR(200) NOT NULL,
    loinc_code      VARCHAR(64),
    ucum_unit       VARCHAR(64),
    value_numeric   NUMERIC(14, 4),
    value_text      VARCHAR(200),
    ref_low         NUMERIC(14, 4),
    ref_high        NUMERIC(14, 4),
    critical_low    NUMERIC(14, 4),
    critical_high   NUMERIC(14, 4),
    flag            VARCHAR(32) NOT NULL,          -- NORMAL | LOW | HIGH | CRITICAL_LOW | CRITICAL_HIGH | ABNORMAL
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_lab_result_value_report ON lab_result_value (report_id);

-- Critical value: the verifier must phone the patient and mark the task done.
CREATE TABLE lab_critical_task (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id       UUID NOT NULL UNIQUE REFERENCES diagnostic_report(id),
    lab_order_id    UUID NOT NULL REFERENCES lab_order(id),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    assignee_id     UUID NOT NULL REFERENCES app_user(id),
    status          VARCHAR(32) NOT NULL DEFAULT 'OPEN', -- OPEN | DONE
    done_by         UUID REFERENCES app_user(id),
    done_at         TIMESTAMPTZ,
    outcome_note    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_lab_critical_task_open ON lab_critical_task (status, assignee_id);

CREATE INDEX IF NOT EXISTS ix_lab_order_status ON lab_order (status);
CREATE INDEX IF NOT EXISTS ix_lab_order_patient ON lab_order (patient_id);
CREATE INDEX IF NOT EXISTS ix_sample_order ON sample (lab_order_id);
