CREATE TABLE IF NOT EXISTS nrc_artifacts(
 id UUID PRIMARY KEY,recording_session_id UUID NOT NULL REFERENCES nrc_recording_sessions(id) ON DELETE CASCADE,
 status VARCHAR(24) NOT NULL CHECK(status IN('PENDING_UPLOAD','VERIFIED','FAILED','ABORTED')),
 upload_mode VARCHAR(16) NOT NULL CHECK(upload_mode IN('SINGLE_PUT','MULTIPART')),
 file_name VARCHAR(255) NOT NULL,content_type VARCHAR(255) NOT NULL,size_bytes BIGINT NOT NULL CHECK(size_bytes>0),
 sha256_hex CHAR(64) NOT NULL CHECK(sha256_hex ~ '^[0-9a-f]{64}$'),bucket VARCHAR(63) NOT NULL,object_key TEXT NOT NULL UNIQUE,
 multipart_upload_id TEXT,part_size_bytes BIGINT,part_count INTEGER,etag TEXT NOT NULL DEFAULT '',failure_reason TEXT NOT NULL DEFAULT '',
 created_at_unix_ms BIGINT NOT NULL,verified_at_unix_ms BIGINT,
 CHECK((upload_mode='SINGLE_PUT' AND multipart_upload_id IS NULL AND part_size_bytes IS NULL AND part_count IS NULL)
 OR(upload_mode='MULTIPART' AND multipart_upload_id IS NOT NULL AND part_size_bytes IS NOT NULL AND part_size_bytes>=5242880 AND part_count IS NOT NULL AND part_count BETWEEN 1 AND 10000))
);
CREATE INDEX IF NOT EXISTS idx_nrc_artifacts_recording_created ON nrc_artifacts(recording_session_id,created_at_unix_ms DESC);
CREATE INDEX IF NOT EXISTS idx_nrc_artifacts_status_created ON nrc_artifacts(status,created_at_unix_ms DESC);
