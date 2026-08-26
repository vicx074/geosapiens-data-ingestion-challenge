CREATE TABLE ingestion_outbox (
    job_id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    claimed_at TIMESTAMPTZ,
    claimed_until TIMESTAMPTZ,
    claim_token UUID,
    published_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    last_error TEXT,

    CONSTRAINT ingestion_outbox_job_fk
        FOREIGN KEY (job_id) REFERENCES ingestion_jobs (id),
    CONSTRAINT ingestion_outbox_status_valid
        CHECK (status IN ('PENDING', 'CLAIMED', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ingestion_outbox_attempts_non_negative
        CHECK (attempts >= 0),
    CONSTRAINT ingestion_outbox_available_after_creation
        CHECK (available_at >= created_at),
    CONSTRAINT ingestion_outbox_timestamps_ordered
        CHECK (
            (claimed_at IS NULL OR claimed_at >= created_at)
            AND (published_at IS NULL OR published_at >= created_at)
            AND (failed_at IS NULL OR failed_at >= created_at)
        ),
    CONSTRAINT ingestion_outbox_state_consistent
        CHECK (
            (status = 'PENDING'
                AND claimed_at IS NULL
                AND claimed_until IS NULL
                AND claim_token IS NULL
                AND published_at IS NULL
                AND failed_at IS NULL)
            OR
            (status = 'CLAIMED'
                AND attempts > 0
                AND claimed_at IS NOT NULL
                AND claimed_until IS NOT NULL
                AND claimed_until > claimed_at
                AND claim_token IS NOT NULL
                AND published_at IS NULL
                AND failed_at IS NULL)
            OR
            (status = 'PUBLISHED'
                AND attempts > 0
                AND claimed_at IS NULL
                AND claimed_until IS NULL
                AND claim_token IS NULL
                AND published_at IS NOT NULL
                AND failed_at IS NULL
                AND last_error IS NULL)
            OR
            (status = 'FAILED'
                AND attempts > 0
                AND claimed_at IS NULL
                AND claimed_until IS NULL
                AND claim_token IS NULL
                AND published_at IS NULL
                AND failed_at IS NOT NULL
                AND last_error IS NOT NULL
                AND BTRIM(last_error) <> '')
        )
);
