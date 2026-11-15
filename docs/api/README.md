# NRC Platform API reference

The versioned OpenAPI 3.1 document is [openapi.json](openapi.json). It references the canonical telemetry envelope schema in `schemas/telemetry-envelope.v1.schema.json`.

## Implemented REST endpoints

- `GET /api/v1/health`
- `GET /api/v1/experiments`
- `POST /api/v1/experiments`
- `GET /api/v1/experiments/{experimentId}`
- `POST /api/v1/experiments/{experimentId}/runs`
- `GET /api/v1/experiments/{experimentId}/runs`
- `GET /api/v1/runs/{runId}`
- `POST /api/v1/runs/{runId}/transition`
- `POST /api/v1/runs/{runId}/recordings`
- `GET /api/v1/runs/{runId}/recordings`
- `GET /api/v1/recordings/{recordingId}`
- `POST /api/v1/recordings/{recordingId}/transition`
- `POST /api/v1/robots/{robotId}/telemetry`

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

Invalid transitions return HTTP 409. Failure transitions require a non-empty failure reason. Recording sessions currently register metadata only; binary upload and S3-compatible object storage are the next implementation increment.

## Implemented WebSocket endpoint

- `WS /api/v1/robots/{robotId}/stream`

Accepted telemetry is broadcast as a JSON `TelemetryEnvelope`. The current stream hub uses a bounded in-memory buffer; it is not a durable recording service.

## Development security boundary

The current MVP is unauthenticated and binds to loopback by default. Do not expose it to a public network. Add authentication, device credentials, TLS, access controls and operational hardening before shared or production deployment.

See [local development instructions](../../deploy/local/README.md) for startup and desktop API-relay testing.
