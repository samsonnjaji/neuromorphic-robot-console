ALTER TABLE nrc_artifacts
    ADD COLUMN upload_url_expires_at_unix_ms BIGINT,
    ADD COLUMN cleanup_completed_at_unix_ms BIGINT;

UPDATE nrc_artifacts
SET upload_url_expires_at_unix_ms = CASE
    WHEN status = 'PENDING_UPLOAD' THEN created_at_unix_ms + 86400000
    ELSE created_at_unix_ms
END
WHERE upload_url_expires_at_unix_ms IS NULL;

ALTER TABLE nrc_artifacts
    ALTER COLUMN upload_url_expires_at_unix_ms SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_nrc_artifacts_upload_expiry
    ON nrc_artifacts(upload_url_expires_at_unix_ms, created_at_unix_ms)
    WHERE status = 'PENDING_UPLOAD';

CREATE INDEX IF NOT EXISTS idx_nrc_artifacts_cleanup_retry
    ON nrc_artifacts(created_at_unix_ms)
    WHERE status = 'ABORTED' AND cleanup_completed_at_unix_ms IS NULL;
