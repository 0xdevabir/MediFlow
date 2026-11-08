-- V2-05: speed least-loaded ER assignee selection for open Red Alerts.
CREATE INDEX IF NOT EXISTS ix_red_alert_assigned_er_open
    ON red_alert (assigned_er_user_id)
    WHERE handover_status IS NULL AND assigned_er_user_id IS NOT NULL;
