# NRC Session Handoff

**Last updated:** 2026-11-15  
**Repository:** https://github.com/samsonnjaji/neuromorphic-robot-console  
**Active branch:** `main`  
**Last verified implementation commit:** `65fbb6194a9dc03e8bebc09172aabee4dafab142`  
**Implementation CI:** [Run 20 — success](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43696350093)  
**Last documentation commit before this handoff refresh:** `a4f2b30aceac95871d601f901c5034eb3874df2c`, [Run 21 — success](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43696578541).

## 1. Instruction for the next ChatGPT session

Continue work in the existing GitHub repository. Do not restart the architecture exercise and do not ask the user to repeat prior decisions. First read this file, then inspect the current `main` branch and the latest GitHub Actions run. The remote repository is the source of truth. The handoff itself may be updated by later documentation commits; always verify the current branch head and latest run before acting.

The user wants to be told explicitly when to pull the code onto their Windows PC and open it in Cursor for application testing. **The local testing gate is met:** the latest implementation CI is green, the Gradle wrapper is included and verified, Docker Compose configuration passes validation, and local PowerShell instructions are committed. The next action is to tell the user to pull and run the desktop API-relay and Android build smoke tests.

Keep this handoff file current after each substantial milestone. Record commit SHAs, CI run links, implemented work, known issues and the next exact task. Do not claim tests passed without checking the run.

## 2. Product direction and agreed architecture

NRC is a cross-platform robotics evaluation, observability and evidence platform for autonomous perception under adverse conditions. It is not intended to replace ROS 2, robot firmware, Foxglove, CARLA, Isaac Sim or specialist ML frameworks.

Agreed target:
- Kotlin Multiplatform `nrc-core`: canonical domain models, experiment definitions, telemetry validation, replay, metrics and scenario evaluation.
- Android and desktop Compose clients.
- Ktor/Kotlin JVM Platform API: identity, device registry, experiment metadata, reports and live telemetry.
- PostgreSQL for metadata; S3-compatible object storage for large recordings and artifacts.
- Separate read-only ROS 2/MCAP/ROS bag edge gateway.
- Separate Python inference workers for conventional detectors and SNNs.
- Web client using the same versioned API.
- Local laboratory, hybrid and cloud-managed deployment modes.

No remote robot actuation in the initial release. Any later control plane needs a separate safety design, controller-side limits, watchdog, fail-safe and physical E-stop.

## 3. Work already committed

### Shared core
- Independent `core/nrc-core` Kotlin Multiplatform module.
- Moved telemetry/domain models, stream guard, safety envelope, mission recording, robustness evaluator and related tests out of the Compose application module.
- Core has no Compose, database, network-client or ROS 2 dependency.
- Stream guard rejects envelope/payload sequence mismatches.

### Data contracts
- Versioned telemetry envelope and experiment manifest JSON Schemas.
- Positive and negative JSON fixtures.
- `scripts/validate_schemas.py`.
- OpenAPI 3.1 document in `docs/api/openapi.json`, with external reference to the telemetry envelope schema.
- CI validates JSON schemas, fixtures, OpenAPI paths and referenced local schemas.

### Platform API MVP
- Ktor service in `services/platform-api`.
- Health endpoint.
- Experiment create/list/read endpoints.
- Telemetry POST ingestion and WebSocket stream endpoint.
- Typed validation, stream sequence guard and structured errors.
- In-memory repository for tests/no-database local development.
- PostgreSQL JDBC repository using HikariCP.
- Flyway migration for experiment metadata.
- Docker Compose PostgreSQL service in `deploy/local/compose.yaml`.
- PowerShell local startup and smoke-test guide in `deploy/local/README.md`.
- PostgreSQL persistence integration test, wired to CI PostgreSQL service.

### Desktop API relay
- Common `NrcTelemetrySource` interface.
- Existing simulator implements the interface and remains the default mode.
- Desktop-only `ApiRelayTelemetrySource` uses Ktor CIO client.
- With `NRC_API_BASE_URL` configured, desktop publishes synthetic simulator telemetry to the API and consumes the same telemetry from the WebSocket.
- API relay dashboard is explicitly labelled `API RELAY · SYNTHETIC` and read-only.
- Android remains in standalone simulator mode.
- Local PowerShell instructions are in `deploy/local/README.md`.

### Build and local developer experience
- Official Gradle 9.7.0 wrapper scripts and wrapper JAR.
- Gradle distribution SHA-256 pinned in `gradle-wrapper.properties`.
- Wrapper JAR SHA-256 checked in CI against Gradle's published checksum.
- `.env` and `.env.*` ignored; `.env.example` retained.
- Docker Compose configuration validated in CI.
- Local commands use `.\gradlew.bat` in PowerShell.

### CI
- Run 14, commit `511c9b3`, passed schema validation, core tests, desktop compile, API tests and Android build.
- Run 15, commit `c202f4b`, passed PostgreSQL persistence, core/desktop/API tests and Android build.
- Run 16, commit `a3c8aa9`, passed the full workflow.
- Run 17, commit `1cc677c`, passed the full workflow including desktop API relay compilation.
- Run 18, commit `5037fd0`, passed the full workflow including Docker Compose config validation.
- Run 19, commit `d198efb`, passed the full workflow including Gradle wrapper checksum and Compose validation.
- Run 20, commit `65fbb61`, passed the full workflow using the checked-in Gradle wrapper: wrapper checksum, JSON Schema/OpenAPI validation, Docker Compose configuration, core tests, desktop compile, API tests, PostgreSQL persistence integration and Android debug build.
- Run 21, documentation commit `a4f2b30`, passed the same full workflow.

## 4. Current limitations — do not misrepresent

- No authentication or device credential flow.
- API is a local MVP and binds to `127.0.0.1` by default.
- PostgreSQL stores experiment metadata only.
- Telemetry WebSocket fan-out is in memory, not durable.
- No S3-compatible object storage or recording upload.
- Desktop API relay mode uses synthetic telemetry; no real robot is connected.
- Android API integration is not implemented; Android currently uses simulator mode.
- No web client, ROS 2 gateway or Python inference runtime yet.
- Robustness Lab profiles are deterministic synthetic perturbations, not calibrated weather physics or measured model accuracy.
- NRC is not a safety-rated robot controller.

## 5. Immediate next tasks

1. Tell the user the build is green and ask them to pull the latest `main` branch into Cursor.
2. Have them start local PostgreSQL and the API using `deploy/local/README.md`.
3. Have them launch desktop with `NRC_API_BASE_URL=http://127.0.0.1:8080` and confirm `API RELAY · SYNTHETIC ONLINE`, live event/LiDAR/IMU/SNN panels and no API errors.
4. Have them run `.\gradlew.bat :androidApp:assembleDebug`; Android remains simulator-only for now.
5. Ask for the first failing terminal output or screenshot if anything does not work. Fix local issues before starting the next major feature.
6. After local feedback, proceed with S3-compatible object storage and recording upload, then authentication/device identity.
7. Keep Android API integration, ROS 2 gateway, Python inference runtime and web client in later milestones.

## 6. Local testing instructions

From the repository root in PowerShell:

**Start PostgreSQL**
```powershell
docker compose -f deploy/local/compose.yaml up -d postgres
```

**Run API in terminal 1**
```powershell
$env:NRC_DATABASE_URL = "jdbc:postgresql://localhost:5432/nrc"
$env:NRC_DATABASE_USER = "nrc"
$env:NRC_DATABASE_PASSWORD = "nrc_local_dev_only"
$env:NRC_API_HOST = "127.0.0.1"
$env:NRC_API_PORT = "8080"
.\gradlew.bat :platform-api:run
```

**Run desktop API relay in terminal 2**
```powershell
$env:NRC_API_BASE_URL = "http://127.0.0.1:8080"
$env:NRC_ROBOT_ID = "nrc-desktop-relay"
.\gradlew.bat :composeApp:run
```

Expected dashboard status: `API RELAY · SYNTHETIC ONLINE`. This is a synthetic relay demonstration, not a physical robot connection.

**Build Android**
```powershell
.\gradlew.bat :androidApp:assembleDebug
```

If a custom `NRC_DB_PASSWORD` is set in the local `.env`, use the same password in `NRC_DATABASE_PASSWORD`.

## 7. Key files

- `docs/PLATFORM_ARCHITECTURE.md` — target and current architecture.
- `docs/PLATFORM_ROADMAP.md` — milestones and completion state.
- `docs/SESSION_HANDOFF.md` — this file; next-chat continuity.
- `docs/ARCHITECTURE.md` — current implementation details.
- `docs/api/openapi.json` — versioned REST and WebSocket API reference.
- `docs/ADR/ADR-001-platform-boundaries.md` — platform boundaries.
- `docs/ADR/ADR-002-data-contracts.md` — versioned data contracts.
- `schemas/` — wire schemas and fixtures.
- `services/platform-api/` — backend.
- `deploy/local/README.md` — local PostgreSQL/API/desktop run instructions.
- `gradle/wrapper/` — pinned Gradle wrapper and checksums.

## 8. Working rules

- Inspect current source before editing; do not guess repository contents.
- Prefer small, reviewable commits with one responsibility each.
- Run and inspect CI after each meaningful code change.
- Preserve the current simulator and existing app behavior.
- Keep Android and desktop builds green.
- Keep raw sensor recordings out of relational tables and unbounded WebSocket payloads.
- Keep synthetic, estimated and measured data clearly distinguished.
- Do not enable remote actuation.
- Update this handoff and roadmap before finishing a substantial work session.
