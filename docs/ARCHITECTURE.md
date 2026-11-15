# NRC Architecture

This document describes the current Kotlin application and local platform API. The target multi-client architecture is documented in [PLATFORM_ARCHITECTURE.md](PLATFORM_ARCHITECTURE.md).

## Current application and core

```text
             ANDROID / DESKTOP CLIENTS
             Compose Multiplatform UI
                       |
             +---------v----------+
             |     composeApp     |
             | dashboard + demo   |
             | telemetry source   |
             +---------+----------+
                       |
             +---------v----------+
             |      nrc-core      |
             | domain + replay    |
             | safety + metrics   |
             +-------------------+

       Local API (separate JVM process)
             Ktor REST + WebSocket
                       |
             +---------v----------+
             |   Platform API    |
             | experiment routes |
             | telemetry routes  |
             +---------+---------+
                       |
             +---------v----------+
             |    PostgreSQL     |
             | experiment meta   |
             +------------------+
```

## Current application principles

1. **Simulation first:** screens and experiment workflows can be exercised without hardware.
2. **Transport independence:** UI and domain models do not depend on ROS 2 libraries.
3. **Shared Kotlin:** state, validation, models and evaluation logic are shared through `nrc-core`.
4. **Observable pipeline:** preserve timestamps, sequence numbers, sensor identity and source metadata.
5. **Evidence before claims:** clearly distinguish simulated estimates from measured sensor/model results.
6. **Safety separation:** UI guards are convenience controls; physical safety is enforced by the robot/controller.
7. **Deterministic replay:** recorded inputs are replayed in timestamp order for repeatable analysis.
8. **Bounded operation:** in-memory recording has an explicit frame cap to avoid unbounded memory growth.

## Current data flow

`DemoTelemetryEngine` in `composeApp` generates a 10 Hz `RobotTelemetry` stream and exposes it through `StateFlow`. The shared Compose UI observes the stream. When mission capture is enabled, snapshots are passed to `MissionRecorder` in `nrc-core`, which stores bounded `RecordedTelemetryFrame` entries with offsets relative to the start of capture. Replay publishes those snapshots back into the simulator telemetry state flow while the simulator loop is paused.

The `RobustnessLabEngine` in `nrc-core` applies a selected `EnvironmentScenario` to a snapshot using `RobustnessEvaluator`. The evaluator creates a deterministic stressed copy of the telemetry, then produces a `StressTestReport` with sensor retention, event-noise, detection-confidence and inference-latency comparisons. The current stress coefficients are illustrative, not calibrated atmospheric or sensor-physics models, and their confidence values are not ground-truth accuracy metrics.

## Local API data flow

The Ktor API is a separate JVM process. It exposes experiment metadata routes and a read-only telemetry ingest/stream path. When `NRC_DATABASE_URL` and database credentials are configured, the application creates a HikariCP connection pool, applies Flyway migrations and uses `JdbcExperimentRepository` for durable experiment metadata. When no database URL is configured, it explicitly falls back to an in-memory repository for local development and tests.

Telemetry ingestion is validated by `TelemetryStreamGuard` for schema version, message type, robot identity, matching envelope/payload sequence and timestamp. Accepted snapshots are published through a bounded in-memory `TelemetryHub` to connected WebSocket subscribers. This live stream is not a durable recording store.

## Desktop API relay test mode

The default desktop launch uses the standalone simulator, preserving the original interactive controls and mission recording. If `NRC_API_BASE_URL` is set, the JVM desktop entry point creates `ApiRelayTelemetrySource`. That source runs the synthetic demo engine, publishes each snapshot to `POST /api/v1/robots/{robotId}/telemetry`, then consumes the corresponding WebSocket stream. The dashboard displays the received telemetry in a read-only mode with the explicit label `API RELAY · SYNTHETIC`.

This mode validates the desktop → REST API → WebSocket → desktop path. It is not a real robot connection, and it does not enable actuation. Android currently remains in standalone simulator mode.

## Current code layout

- `core/nrc-core`: independent Kotlin Multiplatform domain models, telemetry validation, safety-envelope logic, mission recording, robustness evaluation, transport interfaces and core tests.
- `composeApp`: shared Compose presentation, dashboard, simulator source and current application wiring; JVM desktop entry point and API relay source.
- `androidApp`: Android-specific application shell that consumes `composeApp`.
- `services/platform-api`: Ktor service, experiment repository abstraction, in-memory and PostgreSQL repositories, telemetry fan-out, Flyway migrations and API tests.
- `deploy/local`: local PostgreSQL Compose configuration and PowerShell instructions.
- `docs/api`: OpenAPI 3.1 document and API reference.
- `schemas`: versioned JSON contracts and positive/negative fixtures.
- `scripts`: schema and OpenAPI validation utility.

## Not yet implemented

Authentication, tenant boundaries, S3-compatible object storage, recording upload, Android API integration, ROS 2 gateway, Python inference runtime and web client remain planned. See [PLATFORM_ARCHITECTURE.md](PLATFORM_ARCHITECTURE.md), [PLATFORM_ROADMAP.md](PLATFORM_ROADMAP.md) and [SESSION_HANDOFF.md](SESSION_HANDOFF.md).

The first edge gateway is read-only. Any future motion-command channel requires a separate safety architecture and independent validation.
