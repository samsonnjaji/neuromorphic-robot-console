# NRC Session Handoff

**Last updated:** 2026-11-15  
**Repository:** https://github.com/samsonnjaji/neuromorphic-robot-console  
**Base branch:** `main`  
**Active development branch:** `feat/artifact-retention-cleanup`  
**Artifact storage merge commit:** `3300de07779bc8c06329eeae97ee5c5ea8bca3ef`  
**Merged feature PR:** [PR #14 — S3-compatible artifact storage and recording uploads](https://github.com/samsonnjaji/neuromorphic-robot-console/pull/14)  
**Active cleanup branch commit:** `08883f933d18322a151808ac6a1226f217ba9c99`  
**Cleanup PR:** [PR #15 — expire abandoned artifact uploads and retry cleanup](https://github.com/samsonnjaji/neuromorphic-robot-console/pull/15)  
**Push CI:** [Run 66 — success](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43713949230)  
**PR CI:** [Run 67 — success](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43713953804)  
**Security:** GitGuardian checks passed.

## 1. Instruction for the next ChatGPT session

Continue in the existing repository. Do not restart architecture planning or ask the user to repeat decisions. First inspect the current `main` branch, active PR and latest GitHub Actions run; the remote repository is the source of truth.

The user wants to be told explicitly when to pull the code onto their Windows PC and open it in Cursor. The earlier local gate is complete: Windows 11/JDK 17, Gradle 9.7, Android SDK, Docker engine through Ubuntu WSL, PostgreSQL exposed on host port 5435, API health/experiment CRUD, persistence across API restart, desktop REST/WebSocket relay and Android debug build. Artifact storage PR #14 is merged. The current branch adds abandoned-upload cleanup and Flyway V4; **the cleanup feature has not yet been merged or smoke-tested on the user's PC**. PR #15 push and pull-request CI plus GitGuardian checks have passed. Merge PR #15, then ask the user to pull the updated `main` branch and run the combined PostgreSQL V4/RustFS upload, download and cleanup smoke test.

Keep this handoff and roadmap current after substantial milestones. Record commit SHAs, PR links, CI runs, implemented work, limitations and the next exact task. Never claim a test passed without checking its run.

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
- PostgreSQL JDBC repositories using HikariCP for experiment definitions, experiment runs, recording-session metadata and artifact metadata.
- Flyway V1/V2/V3/V4 migrations for experiment definitions, runs, recording sessions, artifact registry and upload-cleanup tracking.
- Validated run lifecycle: CREATED -> RUNNING -> COMPLETED/FAILED/CANCELLED, with CREATED -> CANCELLED.
- Validated recording lifecycle: CREATED -> RECORDING -> FINALIZED/FAILED/CANCELLED, with CREATED -> CANCELLED.
- Run configuration/source revision provenance, result summary and failure reason.
- Recording format/source identity/metadata/sample count and lifecycle timestamps.
- PostgreSQL artifact registry with upload status, file metadata, object key, ETag and expected SHA-256.
- S3-compatible single-part and multipart presigned upload coordination, streaming byte-size/SHA-256 verification and verified-only download URLs.
- RustFS local Compose service with persistent volumes and a non-root volume-permission helper; provider-neutral AWS SDK for Java v2.
- Background artifact-maintenance worker tracks presigned URL expiry, marks abandoned pending uploads `ABORTED`, aborts multipart sessions, deletes stale objects and retries failed cleanup.
- Failed integrity-check objects are eligible for retryable cleanup after their tracked upload URL expiry and grace period.
- Artifact upload API was merged in PR #14; push and PR CI passed, including PostgreSQL persistence, RustFS single/multipart upload, SHA-256 verification and Android build. PR #15 adds retryable cleanup for expired pending/failed uploads; Runs 66/67 passed. Desktop/Android upload UI and real ROS 2 ingestion are not implemented.
- Docker Compose PostgreSQL service in `deploy/local/compose.yaml`.
- PowerShell local startup and smoke-test guide in `deploy/local/README.md`.
- PostgreSQL persistence integration tests, wired to the CI PostgreSQL service.

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
- Run 25, feature push commit `a5d6d59`, passed schema/OpenAPI validation, Compose validation, core/desktop/API tests, PostgreSQL lifecycle persistence integration and Android debug build.
- Run 26, PR validation for PR #13, passed the same full workflow.
- Run 62, cleanup implementation after SQL parameter correction, passed Kotlin/API tests, PostgreSQL migration/persistence/cleanup tests and Android debug build.
- Run 66, final cleanup branch push including local setup documentation, passed the full workflow.
- Run 67, PR #15 validation, passed the full workflow; GitGuardian Security Checks also passed.

## 4. Current limitations — do not misrepresent

- No authentication or device credential flow.
- API is a local MVP and binds to `127.0.0.1` by default.
- PostgreSQL stores experiment definitions, run/recording metadata and artifact registry records. Raw recording binaries are stored in configured S3-compatible object storage, not PostgreSQL.
- Telemetry WebSocket fan-out is in memory, not durable.
- Background cleanup of expired pending/failed uploads is implemented on the active branch; retention policies for verified artifacts are not implemented yet.
- The cleanup worker is a single API-process service. Object deletion is idempotent and retryable; multi-tenant quotas and verified-object lifecycle policies remain future work.
- Desktop API relay mode uses synthetic telemetry; no real robot is connected.
- Android API integration is not implemented; Android currently uses simulator mode.
- No web client, ROS 2 gateway or Python inference runtime yet.
- Robustness Lab profiles are deterministic synthetic perturbations, not calibrated weather physics or measured model accuracy.
- NRC is not a safety-rated robot controller.

## 5. Immediate next tasks

1. Merge [PR #15](https://github.com/samsonnjaji/neuromorphic-robot-console/pull/15); push CI Run 66, PR CI Run 67 and GitGuardian checks are green.
2. Tell the user to pull the merged `main` branch into Cursor. Preserve the existing local PostgreSQL volume and data; retain host port 5435 if native PostgreSQL still owns 5432.
3. Run the combined Windows smoke test for Flyway V4, RustFS single-PUT upload, server-side SHA-256 verification, verified download and background cleanup startup.
4. Fix local issues and update the handoff with the verified result.
5. Implement the read-only ROS 2/MCAP edge gateway and connect a real sensor recording; keep inference workers and scientific model comparison sequenced behind validated ingestion.

## 6. Local testing instructions

The user's Windows checkout is C:\\Users\\njaji\\Projects\\neuromorphic-robot-console. Docker is available through Ubuntu WSL; Windows PowerShell does not expose docker.exe. Native PostgreSQL owns host port 5432, so the NRC container is exposed on host port 5435.

After PR #15 is merged (PR #14 artifact storage is already in main):

1. In PowerShell at the repository root:
   ```powershell
   git switch main
   git pull --ff-only origin main
   if (!(Test-Path .env)) { Copy-Item .env.example .env }
   ```
2. Edit the ignored .env: set NRC_POSTGRES_HOST_PORT=5435; rename the earlier PostgreSQL Compose credential key to NRC_DB_CREDENTIAL while retaining its existing value if reusing the current database volume; populate the RustFS access and secret keys. Never commit .env.
3. Stop old API/desktop processes with Ctrl+C. Start PostgreSQL and RustFS from Ubuntu WSL:
   ```bash
   cd /mnt/c/Users/njaji/Projects/neuromorphic-robot-console
   docker compose -f deploy/local/compose.yaml up -d postgres rustfs
   docker compose -f deploy/local/compose.yaml ps
   ```
4. In PowerShell, load the ignored local environment values and start the API:
   ```powershell
   $env:NRC_DATABASE_URL = "jdbc:postgresql://localhost:5435/nrc"
   $env:NRC_DATABASE_USER = "nrc"
   $LocalEnv = @{}
   Get-Content .env | ForEach-Object {
       if ($_ -match '^\s*([^#=]+)=(.*)$') {
           $LocalEnv[$matches[1].Trim()] = $matches[2].Trim()
       }
   }
   $env:NRC_DATABASE_PASSWORD = $LocalEnv["NRC_DB_CREDENTIAL"]
   $env:NRC_OBJECT_STORE_ENDPOINT = $LocalEnv["NRC_OBJECT_STORE_ENDPOINT"]
   $env:NRC_OBJECT_STORE_ACCESS_KEY = $LocalEnv["NRC_OBJECT_STORE_ACCESS_KEY"]
   $env:NRC_OBJECT_STORE_SECRET_KEY = $LocalEnv["NRC_OBJECT_STORE_SECRET_KEY"]
   $env:NRC_OBJECT_STORE_BUCKET = $LocalEnv["NRC_OBJECT_STORE_BUCKET"]
   $env:NRC_OBJECT_STORE_REGION = $LocalEnv["NRC_OBJECT_STORE_REGION"]
   $env:NRC_OBJECT_STORE_PATH_STYLE = $LocalEnv["NRC_OBJECT_STORE_PATH_STYLE"]
   $env:NRC_OBJECT_STORE_PRESIGN_TTL_SECONDS = $LocalEnv["NRC_OBJECT_STORE_PRESIGN_TTL_SECONDS"]
   $env:NRC_ARTIFACT_PENDING_UPLOAD_TTL_SECONDS = $LocalEnv["NRC_ARTIFACT_PENDING_UPLOAD_TTL_SECONDS"]
   $env:NRC_ARTIFACT_CLEANUP_GRACE_SECONDS = $LocalEnv["NRC_ARTIFACT_CLEANUP_GRACE_SECONDS"]
   $env:NRC_ARTIFACT_CLEANUP_INTERVAL_SECONDS = $LocalEnv["NRC_ARTIFACT_CLEANUP_INTERVAL_SECONDS"]
   $env:NRC_ARTIFACT_CLEANUP_BATCH_SIZE = $LocalEnv["NRC_ARTIFACT_CLEANUP_BATCH_SIZE"]
   $env:NRC_API_HOST = "127.0.0.1"
   $env:NRC_API_PORT = "8080"
   .\gradlew.bat :platform-api:run
   ```
   Flyway applies V4 automatically. The maintenance worker starts only when both PostgreSQL and object storage are configured.
5. Verify the API health endpoint, create/finalize a recording session, upload a small file through the presigned URL, complete it, retrieve a download URL and compare the downloaded SHA-256. Use deploy/local/README.md for the full round-trip commands.
6. For a quick cleanup test, restart the API with presign TTL 60 seconds, pending upload TTL 60 seconds, cleanup grace 0 and interval 1 second. Create a second artifact intent against the finalized recording but do not upload it; after 65 seconds, the artifact GET endpoint should return status ABORTED. Restore the normal values from .env after the test.
7. Build Android using .\gradlew.bat :androidApp:assembleDebug.

Never run docker compose down -v during this test. The API and object store remain localhost-only and unauthenticated. The desktop API relay is still synthetic and read-only; it is not a physical robot connection.

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
- `deploy/local/README.md` — local PostgreSQL/RustFS/API/desktop/artifact upload instructions.
- `gradle/wrapper/` — pinned Gradle wrapper and checksums.

## 8. Working rules

- Inspect current source before editing; do not guess repository contents.
- Prefer small, reviewable commits with one responsibility each.
- Run and inspect CI after each meaningful code change.
- Preserve the current simulator and existing app behavior.
- Keep Android and desktop builds green.
- Keep raw sensor recordings out of relational tables and unbounded WebSocket payloads; use object storage for binary artifacts.
- Keep synthetic, estimated and measured data clearly distinguished.
- Do not enable remote actuation.
- Update this handoff and roadmap before finishing a substantial work session.
