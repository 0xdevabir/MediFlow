-- T06 prescription workflow (M4): draft metadata, per-item AI gate data, safety flags,
-- overrides/acknowledgements, revoke incidents, and DB-level immutability guards.

-- Unknown items (brand not in the DGDA list) are kept in the draft, blank, until the doctor
-- resolves them; they have no generic yet. Signing is blocked while any such item exists.
ALTER TABLE prescription_item ALTER COLUMN generic_id DROP NOT NULL;

CREATE TABLE rx_prescription_meta (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id         UUID NOT NULL UNIQUE REFERENCES prescription(id) ON DELETE CASCADE,
    doctor_user_id          UUID NOT NULL REFERENCES app_user(id),
    input_mode              VARCHAR(16) NOT NULL,          -- TEXT | PHOTO | MANUAL
    photo_object_key        VARCHAR(512),
    photo_content_type      VARCHAR(64),
    typed_text              TEXT,
    amends_prescription_id  UUID REFERENCES prescription(id),
    revoke_reason           TEXT,
    revoked_by              UUID REFERENCES app_user(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE rx_item_meta (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id             UUID NOT NULL UNIQUE REFERENCES prescription_item(id) ON DELETE CASCADE,
    source              VARCHAR(16) NOT NULL,              -- AI | MANUAL
    gate_status         VARCHAR(16) NOT NULL,              -- PREFILLED | NEEDS_REVIEW | UNKNOWN | MANUAL
    resolved            BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by         UUID REFERENCES app_user(id),
    resolved_at         TIMESTAMPTZ,
    conf_drug           NUMERIC(5, 4),
    conf_dose           NUMERIC(5, 4),
    conf_frequency      NUMERIC(5, 4),
    conf_route          NUMERIC(5, 4),
    conf_duration       NUMERIC(5, 4),
    ai_job_id           UUID,
    ai_brand            VARCHAR(200),
    ai_generic          VARCHAR(200),
    ai_strength         VARCHAR(64),
    ai_dose             VARCHAR(64),
    ai_route            VARCHAR(64),
    ai_frequency        VARCHAR(64),
    ai_duration         VARCHAR(64),
    ai_generic_id       UUID,
    ai_outcome          VARCHAR(16),                       -- ACCEPTED | CORRECTED (set at signing)
    position            INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE rx_safety_flag (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id     UUID NOT NULL REFERENCES prescription(id) ON DELETE CASCADE,
    item_id             UUID REFERENCES prescription_item(id) ON DELETE CASCADE,
    check_type          VARCHAR(32) NOT NULL,
    severity            VARCHAR(16) NOT NULL,              -- CRITICAL | WARNING | UNKNOWN
    fingerprint         VARCHAR(255) NOT NULL,
    message_key         VARCHAR(128) NOT NULL,
    detail              TEXT,
    overridable         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_rx_safety_flag_rx ON rx_safety_flag (prescription_id);

CREATE TABLE rx_flag_ack (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id     UUID NOT NULL REFERENCES prescription(id) ON DELETE CASCADE,
    item_id             UUID NOT NULL REFERENCES prescription_item(id) ON DELETE CASCADE,
    fingerprint         VARCHAR(255) NOT NULL,
    override_id         UUID NOT NULL REFERENCES ai_flag_override(id),
    second_confirmed    BOOLEAN NOT NULL DEFAULT FALSE,
    acked_by            UUID NOT NULL REFERENCES app_user(id),
    acked_at            TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    UNIQUE (item_id, fingerprint)
);

CREATE TABLE rx_incident (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id         UUID NOT NULL REFERENCES prescription(id),
    opened_by               UUID NOT NULL REFERENCES app_user(id),
    opened_at               TIMESTAMPTZ NOT NULL,
    review_due_at           TIMESTAMPTZ NOT NULL,          -- opened_at + 24 h
    dispensed_before_revoke BOOLEAN NOT NULL DEFAULT FALSE,
    reason                  TEXT NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_ai_job_status ON ai_job (status);

-- ---------------------------------------------------------------------------
-- Immutability guards (defence in depth; the service layer enforces the same rules).
-- * A prescription can only become SIGNED with signed_by (a doctor) and signed_at: no auto-sign.
-- * After signing, only the status (SIGNED -> AMENDED | ENTERED_IN_ERROR) and revoked_at change.
-- * ENTERED_IN_ERROR is final. Non-draft prescriptions cannot be deleted.
-- * Items can only be inserted / changed / deleted while the parent is a DRAFT.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION rx_guard_prescription() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'prescription % is % and cannot be deleted', OLD.id, OLD.status;
        END IF;
        RETURN OLD;
    END IF;

    IF NEW.status = 'SIGNED' AND (NEW.signed_by IS NULL OR NEW.signed_at IS NULL) THEN
        RAISE EXCEPTION 'prescription % cannot be SIGNED without signer and time', NEW.id;
    END IF;
    IF NEW.status = 'SIGNED' AND OLD.status = 'DRAFT' AND NOT EXISTS (
            SELECT 1 FROM user_role ur WHERE ur.user_id = NEW.signed_by AND ur.role = 'ROLE_DOCTOR') THEN
        RAISE EXCEPTION 'prescription % can only be signed by a doctor', NEW.id;
    END IF;

    IF OLD.status = 'DRAFT' THEN
        RETURN NEW;
    END IF;
    IF OLD.status = 'ENTERED_IN_ERROR' THEN
        RAISE EXCEPTION 'prescription % is entered-in-error and immutable', OLD.id;
    END IF;
    IF NEW.encounter_id IS DISTINCT FROM OLD.encounter_id
       OR NEW.patient_id IS DISTINCT FROM OLD.patient_id
       OR NEW.signed_at IS DISTINCT FROM OLD.signed_at
       OR NEW.signed_by IS DISTINCT FROM OLD.signed_by THEN
        RAISE EXCEPTION 'signed prescription % is immutable', OLD.id;
    END IF;
    IF NEW.status NOT IN ('SIGNED', 'AMENDED', 'ENTERED_IN_ERROR') THEN
        RAISE EXCEPTION 'prescription % cannot go back to %', OLD.id, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_rx_guard_prescription
    BEFORE UPDATE OR DELETE ON prescription
    FOR EACH ROW EXECUTE FUNCTION rx_guard_prescription();

CREATE OR REPLACE FUNCTION rx_guard_prescription_item() RETURNS trigger AS $$
DECLARE
    parent_status VARCHAR(32);
BEGIN
    SELECT status INTO parent_status FROM prescription
     WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.prescription_id ELSE NEW.prescription_id END;
    -- parent already gone (cascade from a draft delete): allow
    IF parent_status IS NULL THEN
        RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
    END IF;
    IF parent_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'items of a % prescription are immutable', parent_status;
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_rx_guard_prescription_item
    BEFORE INSERT OR UPDATE OR DELETE ON prescription_item
    FOR EACH ROW EXECUTE FUNCTION rx_guard_prescription_item();
