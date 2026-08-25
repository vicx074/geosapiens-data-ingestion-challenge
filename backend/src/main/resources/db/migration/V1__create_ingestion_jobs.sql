CREATE TABLE ingestion_jobs (
    id UUID PRIMARY KEY,
    original_filename TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    accepted_rows BIGINT NOT NULL DEFAULT 0,
    rejected_rows BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    queued_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    failure_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ingestion_jobs_original_filename_not_blank
        CHECK (BTRIM(original_filename) <> ''),
    CONSTRAINT ingestion_jobs_status_valid
        CHECK (status IN (
            'RECEIVED',
            'QUEUED',
            'PROCESSING',
            'COMPLETED',
            'COMPLETED_WITH_ERRORS',
            'FAILED'
        )),
    CONSTRAINT ingestion_jobs_counters_non_negative
        CHECK (accepted_rows >= 0 AND rejected_rows >= 0),
    CONSTRAINT ingestion_jobs_version_non_negative
        CHECK (version >= 0),
    CONSTRAINT ingestion_jobs_timestamps_ordered
        CHECK (
            updated_at >= created_at
            AND (queued_at IS NULL OR queued_at >= created_at)
            AND (started_at IS NULL OR (queued_at IS NOT NULL AND started_at >= queued_at))
            AND (finished_at IS NULL OR finished_at >= COALESCE(started_at, queued_at, created_at))
            AND updated_at >= COALESCE(started_at, queued_at, created_at)
        ),
    CONSTRAINT ingestion_jobs_state_consistent
        CHECK (
            (status = 'RECEIVED'
                AND updated_at = created_at
                AND queued_at IS NULL
                AND started_at IS NULL
                AND finished_at IS NULL
                AND failure_reason IS NULL
                AND accepted_rows = 0
                AND rejected_rows = 0)
            OR
            (status = 'QUEUED'
                AND queued_at IS NOT NULL
                AND updated_at = queued_at
                AND started_at IS NULL
                AND finished_at IS NULL
                AND failure_reason IS NULL
                AND accepted_rows = 0
                AND rejected_rows = 0)
            OR
            (status = 'PROCESSING'
                AND queued_at IS NOT NULL
                AND started_at IS NOT NULL
                AND updated_at >= started_at
                AND finished_at IS NULL
                AND failure_reason IS NULL)
            OR
            (status = 'COMPLETED'
                AND queued_at IS NOT NULL
                AND started_at IS NOT NULL
                AND finished_at IS NOT NULL
                AND updated_at = finished_at
                AND failure_reason IS NULL
                AND rejected_rows = 0)
            OR
            (status = 'COMPLETED_WITH_ERRORS'
                AND queued_at IS NOT NULL
                AND started_at IS NOT NULL
                AND finished_at IS NOT NULL
                AND updated_at = finished_at
                AND failure_reason IS NULL
                AND rejected_rows > 0)
            OR
            (status = 'FAILED'
                AND finished_at IS NOT NULL
                AND updated_at = finished_at
                AND failure_reason IS NOT NULL
                AND BTRIM(failure_reason) <> '')
        )
);
