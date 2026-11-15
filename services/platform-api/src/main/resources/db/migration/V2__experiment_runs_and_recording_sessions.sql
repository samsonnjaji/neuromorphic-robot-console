CREATE TABLE IF NOT EXISTS nrc_experiment_runs (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES nrc_experiments(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL CHECK (status IN ('CREATED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    source_revision VARCHAR(128) NOT NULL DEFAULT '',
    configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_summary_json JSONB,
    failure_reason TEXT NOT NULL DEFAULT '',
    created_at_unix_ms BIGINT NOT NULL,
    started_at_unix_ms BIGINT,
    finished_at_unix_ms BIGINT,
    updated_at_unix_ms BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_nrc_experiment_runs_experiment_created
    ON nrc_experiment_runs (experiment_id, created_at_unix_ms DESC);

CREATE TABLE IF NOT EXISTS nrc_recording_sessions (
    id UUID PRIMARY KEY,
    experiment_run_id UUID NOT NULL REFERENCES nrc_experiment_runs(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL CHECK (status IN ('CREATED', 'RECORDING', 'FINALIZED', 'FAILED', 'CANCELLED')),
    format VARCHAR(24) NOT NULL CHECK (format IN ('MCAP', 'ROSBAG2', 'NRC_JSONL', 'OTHER')),
    source_id VARCHAR(255) NOT NULL,
    sample_count BIGINT NOT NULL DEFAULT 0 CHECK (sample_count >= 0),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    failure_reason TEXT NOT NULL DEFAULT '',
    created_at_unix_ms BIGINT NOT NULL,
    started_at_unix_ms BIGINT,
    finished_at_unix_ms BIGINT,
    updated_at_unix_ms BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_nrc_recording_sessions_run_created
    ON nrc_recording_sessions (experiment_run_id, created_at_unix_ms DESC);
