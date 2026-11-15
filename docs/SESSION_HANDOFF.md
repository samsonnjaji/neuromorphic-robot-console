# NRC Session Handoff

**Last updated:** 2026-11-15  
**Repository:** https://github.com/samsonnjaji/neuromorphic-robot-console  
**Active branch:** `main`  
**Current HEAD at handoff creation:** `1cc677c16ad698e7a6c87965801772e530c34fde`  
**Current CI run:** [Run 17 — desktop API relay](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43695557278)  
**Previous fully verified commit:** `a3c8aa9a5b935bcdebeda077c641829e1826fd77`, [Run 16 — success](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43695021846).

## 1. Instruction for the next ChatGPT session

Continue work in the existing GitHub repository. Do not restart the architecture exercise and do not ask the user to repeat prior decisions. First read this file, then inspect the current `main` branch and the latest GitHub Actions run. The remote repository is the source of truth.

The user wants to be told explicitly when to pull the code onto their Windows PC and open it in Cursor for application testing. Do not ask for a local pull until the current main commit has green CI, local run instructions are complete, and there is a concrete app/API test task that needs the user's machine. The user has not yet been asked to pull this desktop API-relay increment.

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

### CI
- Run 14, commit `511c9b3`, passed schema validation, core tests, desktop compile, API tests and Android build.
- Run 15, commit `c202f4b`, passed PostgreSQL persistence, core/desktop/API tests and Android build.
- Run 16, commit `a3c8aa9`, passed the full workflow.
- Run 17, commit `1cc677c`, desktop API-relay/OpenAPI integration. Core, desktop compilation, API tests, PostgreSQL integration and schema/OpenAPI validation passed; Android build passed and runner cleanup was still in progress when this handoff was written. Check the final run conclusion before proceeding.

## 4. Current limitations — do not misrepresent

- No authentication or device credential flow.
- API is a local MVP and binds to `127.0.0.1` by default.
- PostgreSQL stores experiment metadata only.
- Telemetry WebSocket fan-out is in memory, not durable.
- No S3-compatible object storage or recording upload.
- Desktop API relay mode uses synthetic telemetry; no real robot is connected.
- Android API integration is not implemented.
- No web client, ROS 2 gateway or Python inference runtime yet.
- Robustness Lab profiles are deterministic synthetic perturbations, not calibrated weather physics or measured model accuracy.
- NRC is not a safety-rated robot controller.

## 5. Immediate next tasks

1. Check Run 17 final conclusion. Fix any CI issue before local testing.
2. Validate the latest `deploy/local/compose.yaml` config in CI and ensure `.env` is gitignored.
3. Update the roadmap, architecture and handoff with the final verified commit.
4. If all CI checks pass, tell the user to pull `main` into Cursor for the desktop API-relay and Android build smoke tests. Give exact PowerShell commands from `deploy/local/README.md`.
5. Ask the user to confirm the desktop dashboard shows `API RELAY · SYNTHETIC ONLINE`, the event/LiDAR/SNN panels update, and no API errors appear.
6. After local feedback, proceed with S3-compatible object storage and recording upload, then authentication/device identity.
7. Keep Android API integration, ROS 2 gateway, Python inference runtime and web client in later milestones.

## 6. Local test gate — when to involve the user

Before asking the user to pull:
- Latest `main` GitHub Actions run is green, including core tests, desktop compile, API tests, PostgreSQL persistence integration and Android debug build.
- Docker Compose configuration is valid.
- Local database/API/desktop run steps are committed.
- The desktop API-relay mode is present.
- Provide exact PowerShell commands, expected UI status and the one thing to inspect in Cursor.

When ready, say plainly: **“The build is green. Now pull the latest main branch and open it in Cursor; we are ready for local testing.”**

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
