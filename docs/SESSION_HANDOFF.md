# NRC Session Handoff

**Last updated:** 2026-11-15  
**Repository:** https://github.com/samsonnjaji/neuromorphic-robot-console  
**Active branch:** `main`  
**Current HEAD at handoff creation:** `c202f4bde5cf21de0691ab538f7761c91162f98d`  
**Current CI run:** [Run 15 — PostgreSQL integration](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43694820872)  
**Previous verified commit:** `511c9b3ae4cfa128f0e0aaa9db5fe82d99cd6275`, [Run 14 — success](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43694528000).

## 1. Instruction for the next ChatGPT session

Continue work in the existing GitHub repository. Do not restart the architecture exercise and do not ask the user to repeat prior decisions. First read this file, then inspect the current `main` branch and the latest GitHub Actions run. The remote repository is the source of truth.

The user wants to be told explicitly when to pull the code onto their Windows PC and open it in Cursor for application testing. **Do not ask for a local pull yet** unless the current main commit has green CI, local run instructions are complete, and there is a concrete app/API test task that needs the user's machine. The user should not be asked to test a known-broken or incomplete build.

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

### Data contracts
- Versioned telemetry envelope and experiment manifest JSON Schemas.
- Positive and negative JSON fixtures.
- `scripts/validate_schemas.py`.
- GitHub Actions schema validation.

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

### CI
- Run 14, commit `511c9b3`, passed schema validation, core tests, desktop compile and API tests, and Android build.
- Run 15, commit `c202f4b`, adds PostgreSQL persistence, migration and DB integration test. Its result must be checked before claiming this work is verified.

## 4. Current limitations — do not misrepresent

- No authentication or device credential flow.
- API is a local MVP and binds to `127.0.0.1` by default.
- PostgreSQL stores experiment metadata only.
- Telemetry WebSocket fan-out is in memory, not durable.
- No S3-compatible object storage or recording upload.
- Desktop and Android are not yet connected to the Platform API.
- No web client, ROS 2 gateway or Python inference runtime yet.
- Robustness Lab profiles are deterministic synthetic perturbations, not calibrated weather physics or measured model accuracy.
- NRC is not a safety-rated robot controller.

## 5. Immediate next tasks

1. Check Run 15. Fix any Gradle, Flyway, PostgreSQL or integration-test failures before adding more scope.
2. Verify `docker compose -f deploy/local/compose.yaml config` and the PowerShell instructions.
3. Add a versioned OpenAPI specification for the implemented API routes.
4. Add a desktop API client in a small isolated change. Keep the simulator as an explicit demo source and make API mode selectable; do not silently remove existing simulator functionality.
5. Connect desktop to the API and display live telemetry/experiment records.
6. Only then ask the user to pull the repository into Cursor for a concrete desktop/API test.
7. After local test feedback, proceed to recording/object storage, auth/device identity, ROS 2 gateway and inference runtime.

## 6. Local test gate — when to involve the user

Before asking the user to pull:
- Latest `main` GitHub Actions run is green, including core tests, desktop compile, API tests, PostgreSQL persistence integration and Android debug build.
- Docker Compose configuration is valid.
- Local database/API run steps are committed and have been checked against the actual code.
- The desktop API integration exists or the user is specifically being asked to test a standalone API milestone.
- Provide exact PowerShell commands, expected outputs and the one thing to inspect in Cursor.

When ready, say plainly: **“The build is green. Now pull the latest main branch and open it in Cursor; we are ready for local testing.”**

## 7. Key files

- `docs/PLATFORM_ARCHITECTURE.md` — target and current architecture.
- `docs/PLATFORM_ROADMAP.md` — milestones and completion state.
- `docs/ARCHITECTURE.md` — current implementation details.
- `docs/SESSION_HANDOFF.md` — this file; next-chat continuity.
- `docs/ADR/ADR-001-platform-boundaries.md` — platform boundaries.
- `docs/ADR/ADR-002-data-contracts.md` — versioned data contracts.
- `schemas/` — wire schemas and fixtures.
- `services/platform-api/` — backend.
- `deploy/local/README.md` — local PostgreSQL/API run instructions.

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
