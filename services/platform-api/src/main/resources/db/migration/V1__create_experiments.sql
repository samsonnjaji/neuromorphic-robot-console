CREATE TABLE IF NOT EXISTS nrc_experiments (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    scenario VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(255) NOT NULL,
    model_identifier VARCHAR(255) NOT NULL,
    model_version VARCHAR(128) NOT NULL,
    created_at_unix_ms BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_nrc_experiments_created_at
    ON nrc_experiments (created_at_unix_ms DESC);
