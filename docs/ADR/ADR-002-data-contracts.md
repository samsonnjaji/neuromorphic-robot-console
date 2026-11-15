# ADR-002: Versioned telemetry and experiment contracts

- **Status:** Accepted
- **Date:** 2026-11-15
- **Decision owner:** NRC project

## Context

NRC needs Android, desktop, web, edge gateways and inference services to exchange data without sharing internal implementation details. Sensor recordings can be high volume, while live dashboards need low-latency updates. Experiment reports must retain source and model provenance.

## Decision

- Maintain versioned JSON Schemas for telemetry envelopes and experiment manifests.
- Use REST/HTTPS for control-plane operations and WebSocket/WSS for live telemetry/status messages.
- Use object-storage or chunked upload for MCAP, ROS bag and large media recordings.
- Preserve source timestamps, sequence numbers, robot/device identity, model identity, units and coordinate-frame metadata.
- Reject unsupported schema versions and stale/duplicate sequences at ingestion boundaries.
- Separate synthetic values, estimates and measured values in the schema and UI.
- Treat a manifest as a record of experiment inputs and configuration, not as proof that a run is reproducible until all referenced artifacts are retained.

## Initial telemetry envelope

The first contract retains the existing fields: `schemaVersion`, `messageType`, `robotId`, `sequence`, `timestampUnixMs` and `payload`.

The first payload represents the current RobotTelemetry model. The JSON Schema files are normative for v1 wire validation; Kotlin data classes remain implementation models.

## Future compatibility

A breaking wire change requires a new schema version or message type. Optional additive fields can be introduced only when older clients safely ignore unknown properties or the schema explicitly permits them. Do not silently reinterpret units, timestamps or coordinate frames.

## Consequences

- All clients and gateways share a stable contract.
- Raw sensor data is not forced through the interactive telemetry channel.
- Schema fixtures can be used for contract tests in Kotlin, Python and TypeScript.
- The project must maintain schema versioning and compatibility tests.
