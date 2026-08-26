CREATE INDEX ingestion_outbox_pending_idx
    ON ingestion_outbox (available_at, job_id)
    WHERE status = 'PENDING';

CREATE INDEX ingestion_outbox_expired_claim_idx
    ON ingestion_outbox (claimed_until, job_id)
    WHERE status = 'CLAIMED';

ALTER TABLE ingestion_outbox
    ADD CONSTRAINT ingestion_outbox_last_error_bounded
    CHECK (last_error IS NULL OR CHAR_LENGTH(last_error) <= 1000);
