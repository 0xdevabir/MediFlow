-- V2-02: speed assignee lookups used by AccessFacts.erAssignedOnActiveAlert
CREATE INDEX IF NOT EXISTS ix_red_alert_assigned_er_active
    ON red_alert (assigned_er_user_id)
    WHERE handover_status IS NULL;
