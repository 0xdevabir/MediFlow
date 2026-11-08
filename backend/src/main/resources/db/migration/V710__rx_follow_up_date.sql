-- Optional follow-up date on a prescription draft / signed Rx (publishes FollowUpScheduled on sign).
ALTER TABLE rx_prescription_meta
    ADD COLUMN IF NOT EXISTS follow_up_date DATE;
