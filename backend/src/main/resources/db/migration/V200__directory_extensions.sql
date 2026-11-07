-- T03 directory (D6): columns the baseline lacks + integrity guards.
-- The T00 JPA entities do not map these columns; the directory module reads/writes them via JDBC
-- (see com.mediflow.directory.repo.DirectoryJdbc). ddl-auto=validate ignores unmapped columns.

-- Branches: a hospital row with a parent is a branch / satellite centre of that hospital.
ALTER TABLE hospital ADD COLUMN parent_hospital_id UUID REFERENCES hospital(id);
ALTER TABLE hospital ADD CONSTRAINT ck_hospital_not_own_parent
    CHECK (parent_hospital_id IS NULL OR parent_hospital_id <> id);
-- F7: emergency AND ambulance numbers on every hospital card.
ALTER TABLE hospital ADD COLUMN ambulance_phone VARCHAR(64);
CREATE INDEX ix_hospital_parent ON hospital(parent_hospital_id);

-- Research §5 (BIRDEM): the patient-facing list shows clinical departments only.
ALTER TABLE department ADD COLUMN clinical BOOLEAN NOT NULL DEFAULT TRUE;

-- Doctor card shows speciality / qualification line.
ALTER TABLE practitioner ADD COLUMN specialty VARCHAR(200);

-- Slot generation must never create duplicates (also guards concurrent job + admin edit).
CREATE UNIQUE INDEX ux_slot_role_start ON slot(practitioner_role_id, starts_at);
CREATE INDEX ix_slot_starts_at ON slot(starts_at);
CREATE INDEX ix_practitioner_role_hosp_dept ON practitioner_role(hospital_id, department_id);
ALTER TABLE slot ADD CONSTRAINT ck_slot_time_order CHECK (ends_at > starts_at);
ALTER TABLE slot ADD CONSTRAINT ck_slot_counts CHECK (capacity >= 0 AND booked_count >= 0);

-- F3: a package may only bundle tests that are direct-allowed (defence in depth; the service
-- validates first and shows a friendly message).
CREATE FUNCTION directory_check_package_item() RETURNS trigger AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM lab_test t WHERE t.id = NEW.test_id AND t.direct_allowed) THEN
        RAISE EXCEPTION 'lab_package_item: test % is not direct_allowed', NEW.test_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_lab_package_item_direct_only
    BEFORE INSERT OR UPDATE ON lab_package_item
    FOR EACH ROW EXECUTE FUNCTION directory_check_package_item();

-- A test that is part of a package cannot lose its direct_allowed flag.
CREATE FUNCTION directory_check_test_unflag() RETURNS trigger AS $$
BEGIN
    IF OLD.direct_allowed AND NOT NEW.direct_allowed
       AND EXISTS (SELECT 1 FROM lab_package_item i WHERE i.test_id = NEW.id) THEN
        RAISE EXCEPTION 'lab_test %: still part of a package, cannot clear direct_allowed', NEW.id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_lab_test_keep_direct_in_package
    BEFORE UPDATE OF direct_allowed ON lab_test
    FOR EACH ROW EXECUTE FUNCTION directory_check_test_unflag();
