# NRC Platform API reference

The versioned OpenAPI 3.1 document is [openapi.json](openapi.json). It references the canonical telemetry envelope schema in `schemas/telemetry-envelope.v1.schema.json`.

## Implemented REST endpoints

### Health and experiment lifecycle
- `GET /api/v1/health`
- `GET /api/v1/experiments`
- `POST /api/v1/experiments`
- `GET /api/v1/experiments/{experimentId}`
- `POST /api/v1/experiments/{experimentId}/runs`
- `GET /api/v1/experiments/{experimentId}/runs`
- `GET /api/v1/runs/{runId}`
- `POST /api/v1/runs/{runId}/transition`

### Recording-session lifecycle
- `POST /api/v1/runs/{runId}/recordings`
- `GET /api/v1/runs/{runId}/recordings`
- `GET /api/v1/recordings/{recordingId}`
- `POST /api/v1/recordings/{recordingId}/transition`

### Artifact registry and object upload
- `POST /api/v1/recordings/{recordingId}/artifacts`
- `GET /api/v1/recordings/{recordingId}/artifacts`
- `GET /api/v1/artifacts/{artifactId}`
- `POST /api/v1/artifacts/{artifactId}/upload-url`
- `POST /api/v1/artifacts/{artifactId}/parts/{partNumber}/upload-url`
- `POST /api/v1/artifacts/{artifactId}/complete`
- `POST /api/v1/artifacts/{artifactId}/download-url`

### Telemetry
- `POST /api/v1/robots/{robotId}/telemetry`
- `WS /api/v1/robots/{robotId}/stream`

## Experiment and recording lifecycle

An experiment is a reusable definition. Each execution is stored as an experiment run with its own configuration snapshot, source revision, lifecycle status and optional result summary.

Allowed experiment run transitions:
- `CREATED → RUNNING`
- `RUNNING → COMPLETED | FAILED | CANCELLED`
- `CREATED → CANCELLED`

A recording session belongs to one experiment run and records its format, source identity, metadata, sample count and lifecycle timestamps. Supported formats are `MCAP`, `ROSBAG2`, `NRC_JSONL` and `OTHER`.

Allowed recording transitions:
- `CREATED → RECORDING`
- `RECORDING → FINALIZED | FAILED | CANCELLED`
- `CREATED → CANCELLED`

Invalid lifecycle transitions return HTTP 409. Failure transitions require a non-empty failure reason. Binary artifacts can only be attached to a `FINALIZED` recording session.

## Artifact upload and integrity

The API stores artifact metadata in PostgreSQL and binary objects in an S3-compatible object store. The local development provider is RustFS; the implementation uses the AWS SDK for Java v2 and can target other compatible S3 providers through configuration.

1. Create an artifact against a finalized recording session with its file name, content type, byte size and expected SHA-256 hex digest.
2. For objects up to 64 MiB, the API returns a short-lived presigned PUT URL.
3. Larger objects use S3 multipart upload. The API chooses a part size that respects the S3 10,000-part limit; clients request a presigned URL for each part and retain each returned ETag.
4. Submit the completion request. The API completes multipart uploads when applicable, reads the object as a stream, checks the actual byte count and computes SHA-256.
5. Only artifacts with matching size and checksum transition to `VERIFIED`. A failed integrity check marks the artifact `FAILED` and attempts to delete the object.
6. A short-lived download URL is issued only for a verified artifact.

Presigned URLs are bearer credentials. Keep them private and respect their expiry. The default lifetime is 900 seconds and is configurable. The current API supports S3's 5 TiB maximum object size. A background worker tracks upload URL expiry, waits for a default 24-hour inactivity window plus a 1-hour grace period, aborts abandoned multipart uploads, deletes stale/failed objects and retries cleanup after storage errors. The sweep interval defaults to 15 minutes. Configure these values with the `NRC_ARTIFACT_*` environment variables. Verified artifacts are not deleted by this worker; verified-object retention policies are future work.

The API does not proxy the upload body. Clients upload directly to object storage using the presigned URL. The API streams the object back from storage during integrity verification without loading it fully into memory.

## WebSocket telemetry

Accepted telemetry is broadcast as a JSON `TelemetryEnvelope`. The current stream hub uses a bounded in-memory buffer; it is not a durable recording service. Use recording artifacts for long-running raw sensor data.

## Development security boundary

The current MVP is unauthenticated and binds to loopback by default. Do not expose it to a public network. Presigned URLs are temporary bearer tokens, not a substitute for user/device authentication. Add authentication, device credentials, TLS, access controls and operational hardening before shared or production deployment.

See [local development instructions](../../deploy/local/README.md) for PostgreSQL, RustFS, API and desktop setup.
