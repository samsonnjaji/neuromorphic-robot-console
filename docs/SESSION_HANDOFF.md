# NRC Session Handoff

**Last updated:** 2026-11-16  
**Repository:** https://github.com/samsonnjaji/neuromorphic-robot-console  
**Base branch:** `main`  
**Main commit:** `9f060271e0eafac078cb20c64748e42f4630d487`  
**Active development branch:** `feat/edge-ros2-bag-capture` (remote implementation in progress; PR pending). PRs #16 and #17 are merged.  
**Artifact storage merge commit:** `3300de07779bc8c06329eeae97ee5c5ea8bca3ef`  
**Artifact cleanup merge commit:** `e4c945f6aa9f8eaccfc816da48ac48ca92675e3e`  
**Merged PRs:** [PR #14 — S3-compatible artifact storage](https://github.com/samsonnjaji/neuromorphic-robot-console/pull/14); [PR #15 — automatic abandoned-upload cleanup](https://github.com/samsonnjaji/neuromorphic-robot-console/pull/15); [PR #16 — offline MCAP ingestion gateway](https://github.com/samsonnjaji/neuromorphic-robot-console/pull/16); [PR #17 — provenance v2 and synthetic MCAP fixture](https://github.com/samsonnjaji/neuromorphic-robot-console/pull/17)  
**Cleanup push CI:** [Run 68 — success](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43714195321)  
**Cleanup PR CI:** [Run 69 — success](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43714200783)  
**Main CI:** [Run 70 — success](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43714385506); [Run 71 — success](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43714476010)  
**Gateway PR CI:** [Run 116 — success](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43804370558)  
**Provenance PR:** [PR #17 — merged](https://github.com/samsonnjaji/neuromorphic-robot-console/pull/17); merge commit `b31478332fe08924630cb83a73ae9ca94de4ac26`; [CI Run 191 — success](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43806042612).  
**Security:** GitGuardian checks passed.

## 1. Instruction for the next ChatGPT session

Continue in the existing repository. Do not restart architecture planning or ask the user to repeat decisions. First inspect the current `main` branch, active PR and latest GitHub Actions run; the remote repository is the source of truth.

The user wants to be told explicitly when to pull the code onto their Windows PC and open it in Cursor. The earlier local baseline is complete: Windows 11/JDK 17, Gradle 9.7, Android SDK, Docker engine through Ubuntu WSL, PostgreSQL exposed on host port 5435, API health/experiment CRUD, persistence across API restart, desktop REST/WebSocket relay and Android debug build. PRs #14–#17 are merged, including S3-compatible storage, abandoned-upload cleanup, offline MCAP ingestion and provenance manifest v2. **The combined PostgreSQL V4/RustFS workflow and gateway-to-Ktor/RustFS synthetic MCAP import have not yet been smoke-tested on the user's PC.** When the workstation is available, ask the user to pull current `main`, preserve the existing PostgreSQL/object-store volumes, and run both local tests. Do not ask them to pull an unmerged feature branch.

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
- Artifact upload API was merged in PR #14; push and PR CI passed, including PostgreSQL persistence, RustFS single/multipart upload, SHA-256 verification and Android build. PR #15 merged Flyway V4 expiry tracking and retryable cleanup for expired pending/failed uploads; Runs 68/69 passed. Desktop/Android upload UI and real ROS 2 ingestion are not implemented.
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

### Edge Gateway v1 and provenance v2 — PRs #16 and #17 merged

PR #16 added offline MCAP ingestion. PR #17 preserved manifest v1 and introduced manifest v2 with explicit data origin, a synthetic fixture generator and an accidental-misclassification guard. PR #17 CI Run #191 passed. The local API/RustFS and synthetic MCAP end-to-end smoke tests remain pending.

PR #16 delivers the offline MCAP importer. PR #17 adds immutable `edge-recording-manifest.v2`, separating source origin (`PHYSICAL_SENSOR`, `SIMULATED`, `DATASET`, `UNKNOWN`) from ingestion mode (`RECORDED`), plus a labelled synthetic MCAP fixture generator with ROS 2 CDR and QoS metadata. The original v1 schema remains unchanged for historical compatibility. PR #17 merged as `b31478332fe08924630cb83a73ae9ca94de4ac26`; GitHub Actions Run #191 passed.

Branch: `feat/edge-mcap-ingestion-v1`.

The first Python gateway increment adds offline MCAP framing validation, channel/topic/schema indexing, per-channel message and sequence counts, separate publish/log timestamp ranges in nanoseconds, streaming SHA-256, a versioned recording manifest, and an NRC API client for single-part and multipart artifact upload. The ingestion workflow registers an experiment run and MCAP recording session, uploads the untouched MCAP plus manifest, and completes the run only after the API reports both artifacts `VERIFIED`.

Automated tests cover MCAP inspection, zero publish-time handling, manifest schema validation, single/multipart upload planning and API lifecycle orchestration. PR #16 merged as `dae6fa3cd4badc9196ccd296eae77dbe7f4d65ff`. GitHub Actions Run #116 passed the Python gateway tests, manifest contract validation, existing Kotlin/core/API/PostgreSQL/RustFS integration checks, Docker Compose validation and Android debug build. The local gateway-to-Ktor/RustFS smoke test is still pending.

This is offline MCAP ingestion only. Live ROS 2 subscriptions, CDR payload decoding, conversion into the dashboard `RobotTelemetry` model, live bounded buffering/reconnection, ROS bag ingestion and real-sensor validation remain outstanding. The manifest distinguishes `dataOrigin` (`PHYSICAL_SENSOR`, `SIMULATED`, `DATASET`, `UNKNOWN`) from `ingestionMode` (`RECORDED`); it never assumes an MCAP file is physical sensor data. Frame IDs or units embedded inside serialized payloads are not decoded in this increment; the original recording remains untouched.

## 4. Current limitations — do not misrepresent

- No authentication or device credential flow.
- API is a local MVP and binds to `127.0.0.1` by default.
- PostgreSQL stores experiment definitions, run/recording metadata and artifact registry records. Raw recording binaries are stored in configured S3-compatible object storage, not PostgreSQL.
- Telemetry WebSocket fan-out is in memory, not durable.
- Background cleanup of expired pending/failed uploads is merged into `main` via PR #15; retention policies for verified artifacts are not implemented yet.
- The cleanup worker is a single API-process service. Object deletion is idempotent and retryable; multi-tenant quotas and verified-object lifecycle policies remain future work.
- Desktop API relay mode uses synthetic telemetry; no real robot is connected.
- Android API integration is not implemented; Android currently uses simulator mode.
- No live ROS 2 subscriptions/decoder, web client or Python inference runtime yet. Offline MCAP ingestion v1 is merged via PR #16; provenance manifest v2 and the labelled synthetic fixture are merged via PR #17. The local gateway-to-Ktor/RustFS synthetic import remains unverified on the user's PC.
- Robustness Lab profiles are deterministic synthetic perturbations, not calibrated weather physics or measured model accuracy.
- NRC is not a safety-rated robot controller.

## 5. Immediate next tasks

1. Complete PR #18 CI for the read-only rosbag2/MCAP capture adapter and Sensor Observation Envelope v1.
2. Merge only after the full CI workflow passes.
3. When the workstation is available, pull current main and run the PostgreSQL V4/RustFS and synthetic MCAP importer smoke tests.
4. In WSL/Linux with ROS 2 and the MCAP storage plugin installed, validate one real, explicitly allowlisted sensor recording.
5. Add selected typed decoders and a future API ingestion path for Sensor Observation Envelope; do not coerce raw messages into RobotTelemetry.
6. Only then proceed to live streaming, inference workers and scientific model comparisons.

## 6. Local testing instructions

The user's Windows checkout is C:\\Users\\njaji\\Projects\\neuromorphic-robot-console. Docker is available through Ubuntu WSL; Windows PowerShell does not expose docker.exe. Native PostgreSQL owns host port 5432, so the NRC container is exposed on host port 5435.

PRs #15, #16 and #17 are already merged; the following procedure applies to current `main`:

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

## 7. Edge gateway synthetic MCAP smoke test

After the Platform API is running locally with PostgreSQL and RustFS configured, open a second PowerShell terminal at the repository root. Python 3.12 or newer is required:

```powershell
python --version
python -m pip install -e services/edge-gateway
python services/edge-gateway/examples/create_synthetic_mcap.py --output .\\synthetic-demo.mcap --messages 10
nrc-edge inspect .\\synthetic-demo.mcap
nrc-edge ingest .\\synthetic-demo.mcap --api-url http://127.0.0.1:8080 --robot-id nrc-demo-robot --source-id synthetic-fixture --scenario CLEAR --data-origin SIMULATED
```

The import must return an experiment ID, run ID, recording-session ID, MCAP artifact ID and manifest artifact ID. The CLI only exits successfully after both artifacts are `VERIFIED` and the run reaches `COMPLETED`. This is a synthetic integration fixture, not a physical robot test. Preserve the local PostgreSQL/RustFS volumes throughout.

## 8. Key files

- `schemas/sensor-observation.v1.schema.json`
- `docs/SENSOR_OBSERVATION_CONTRACT.md`
- `docs/ADR/ADR-003-sensor-observation-envelope.md`
- `services/edge-gateway/src/nrc_edge_gateway/ros2_capture.py`

- `schemas/sensor-observation.v1.schema.json`
- `docs/SENSOR_OBSERVATION_CONTRACT.md`
- `docs/ADR/ADR-003-sensor-observation-envelope.md`
- `services/edge-gateway/src/nrc_edge_gateway/ros2_capture.py`

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
- `services/edge-gateway/` — Python offline MCAP inspection and ingestion CLI.
- `docs/EDGE_GATEWAY.md` — edge-gateway scope, data flow and next increments.
- `schemas/edge-recording-manifest.v2.schema.json` — current MCAP provenance contract; v1 remains historical and unchanged.
- `gradle/wrapper/` — pinned Gradle wrapper and checksums.

## 9. Working rules

- Inspect current source before editing; do not guess repository contents.
- Prefer small, reviewable commits with one responsibility each.
- Run and inspect CI after each meaningful code change.
- Preserve the current simulator and existing app behavior.
- Keep Android and desktop builds green.
- Keep raw sensor recordings out of relational tables and unbounded WebSocket payloads; use object storage for binary artifacts.
- Keep synthetic, estimated and measured data clearly distinguished.
- Do not enable remote actuation.
- Update this handoff and roadmap before finishing a substantial work session.
