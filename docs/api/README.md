# NRC Platform API reference

The versioned OpenAPI 3.1 document is [openapi.json](openapi.json). It references the canonical telemetry envelope schema in `schemas/telemetry-envelope.v1.schema.json`.

## Implemented REST endpoints

- `GET /api/v1/health`
- `GET /api/v1/experiments`
- `POST /api/v1/experiments`
- `GET /api/v1/experiments/{experimentId}`
- `POST /api/v1/robots/{robotId}/telemetry`

## Implemented WebSocket endpoint

- `WS /api/v1/robots/{robotId}/stream`

Accepted telemetry is broadcast as a JSON `TelemetryEnvelope`. The current stream hub uses a bounded in-memory buffer; it is not a durable recording service.

## Development security boundary

The current MVP is unauthenticated and binds to loopback by default. Do not expose it to a public network. Add authentication, device credentials, TLS, access controls and operational hardening before shared or production deployment.

See [local development instructions](../../deploy/local/README.md) for startup and desktop API-relay testing.
