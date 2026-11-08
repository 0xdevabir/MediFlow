-- V2-06: follow-up date for an encounter (shared Encounter/ClinicalNote stay untouched).
CREATE TABLE clinical_follow_up (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id    UUID NOT NULL UNIQUE REFERENCES encounter(id),
    patient_id      UUID NOT NULL REFERENCES patient(id),
    follow_up_date  DATE NOT NULL,
    note_id         UUID REFERENCES clinical_note(id),
    scheduled_by    UUID NOT NULL REFERENCES app_user(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX clinical_follow_up_patient_date_idx
    ON clinical_follow_up (patient_id, follow_up_date);
