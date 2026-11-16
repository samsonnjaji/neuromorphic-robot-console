# NRC Platform Implementation Roadmap

This roadmap sequences platform work so that every phase produces a usable, testable increment. Competition success is not a dependency for continuing the product.

## Phase 0 — Platform foundation

**Goal:** agree on boundaries before adding services.

- [x] Document multi-client platform architecture.
- [x] Record architecture decisions for platform boundaries and data contracts.
- [x] Define initial telemetry and experiment JSON schemas.
- [x] Add positive and negative schema fixtures and CI validation.
- [x] Add an OpenAPI 3.1 document for implemented REST endpoints and a WebSocket extension.
- [x] Agree the core module extraction plan.

**Exit criteria:** the team can explain which component owns each responsibility and how every component exchanges data.

## Phase 1 — Extract NRC Core

**Goal:** isolate the intelligence and experiment logic from presentation.

- [x] Create `core/nrc-core` as an independent Kotlin Multiplatform module.
- [x] Move domain models, telemetry validation, safety-envelope logic, mission recording and robustness evaluation into core.
- [ ] Define stable public use cases/interfaces for simulator, experiment evaluation and telemetry validation.
- [x] Keep Compose screens and demo application wiring in the current client module.
- [x] Keep the core independent of Compose UI, network clients, databases and ROS 2 libraries.
- [x] Keep KMP JVM tests and Android application build green.

**Exit criteria:** core tests run without UI, network, database or robot dependencies. Passed in CI at commit `d98a28d`.

## Phase 2 — Local Platform API

**Goal:** make the applications clients of a real backend.

- [x] Create a Ktor service with `GET /api/v1/health`.
- [x] Add experiment create/read/list endpoints.
- [x] Add telemetry ingestion and WebSocket fan-out.
- [x] Add typed request validation and structured errors.
- [x] Add PostgreSQL experiment metadata repository and Flyway migration.
- [x] Add local Docker Compose PostgreSQL service.
- [x] Add API endpoint tests and PostgreSQL persistence integration test.
- [x] Add OpenAPI 3.1 route reference and external schema checks.
- [x] Add a desktop API-relay source that sends synthetic telemetry through REST and receives it over WebSocket.
- [x] Add the Gradle 9.7.0 wrapper and verify its JAR checksum.
- [x] Add PowerShell startup instructions and validate Docker Compose configuration in CI.
- [x] Complete desktop API-relay and Android build smoke tests on the user's Windows PC in Cursor (verified 2026-11-15; Docker engine accessed through Ubuntu WSL and PostgreSQL exposed on host port 5435).
- [x] Add persistent experiment-run lifecycle and validated state transitions (PR #13; GitHub Actions Run 26 passed).
- [x] Add recording-session metadata lifecycle and PostgreSQL persistence (PR #13; GitHub Actions Run 26 passed).
- [x] Add artifact registry, S3-compatible object storage and recording upload coordination.
- [x] Add presigned single-part and multipart uploads with server-side size/SHA-256 verification.
- [x] Add local RustFS Compose service and CI integration tests.
- [x] Add expiry tracking and background cleanup for abandoned pending/failed uploads, including multipart abort and retryable object deletion.
- [ ] Add retention policies and quota-based lifecycle management for verified artifacts.
- [ ] Add authentication and device credentials.
- [ ] Verify Android API integration after desktop behavior is validated.

**Local baseline verification:** the user completed Windows 11/JDK 17, PostgreSQL, API, desktop REST/WebSocket relay and Android debug build smoke tests on 2026-11-15.

**Experiment/recording lifecycle verification:** PR #13 was merged into main at `1c6dad8629a9d4df5029e3695c6a7fc9d533306b`; [GitHub Actions Run 25](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43707369909) and [PR validation Run 26](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43707445882) passed.

**Artifact storage verification:** PR #14 was squash-merged into `main` at `3300de07779bc8c06329eeae97ee5c5ea8bca3ef`. Its push and pull-request CI passed the complete Kotlin, PostgreSQL, RustFS single/multipart upload, SHA-256 verification and Android build workflow; GitGuardian checks passed.

**Artifact cleanup verification:** PR #15 adds Flyway V4 expiry/cleanup metadata, tracked presigned URL expiry, a periodic worker, multipart abort, idempotent object deletion and retry of failed cleanup. [Push CI Run 66](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43713949230) and [PR CI Run 67](https://github.com/samsonnjaji/neuromorphic-robot-console/actions/runs/43713953804) passed the full workflow, including Kotlin/API tests, PostgreSQL migration/persistence/cleanup tests, RustFS integration, schema/Compose validation and Android debug build. GitGuardian checks passed.

**Current gate:** PR #15 is merged at `e4c945f6aa9f8eaccfc816da48ac48ca92675e3e`. Ask the user to pull the updated `main` into Cursor. The combined local smoke test should apply Flyway V4, start PostgreSQL/RustFS, verify a single-PUT upload/download SHA-256 round trip, and confirm the cleanup worker aborts a deliberately expired pending upload without touching verified objects. Preserve the existing PostgreSQL volume and host port 5435.

**Exit criteria:** storage and cleanup pass CI and the local V4 smoke test. Then proceed to the read-only ROS 2/MCAP edge gateway and one real sensor recording; verified-object retention remains a separate future milestone.

## Phase 3 — Edge and real data

**Goal:** connect real robotics data without enabling control.

- [x] Implement offline MCAP framing validation and channel/topic/schema indexing (merged PR #16; CI Run #116 passed).
- [x] Preserve MCAP publish and log timestamps separately in nanoseconds, with channel message counts and sequence ranges.
- [x] Generate a versioned provenance manifest with explicit data origin and recorded-ingestion mode; upload the original MCAP plus manifest through the verified artifact workflow.
- [ ] Complete the local gateway-to-Ktor/RustFS synthetic MCAP smoke test.
- [ ] Complete read-only ROS 2 bag capture through the rosbag2 MCAP storage plugin (PR #19; CI pending).
- [ ] Define Sensor Observation Envelope v1 to preserve per-topic source/reception timestamps, frame IDs, units, payload references and origin without padding RobotTelemetry (PR #19; CI pending).
- [ ] Decode selected ROS 2 message types and preserve source timestamps, frame IDs, units and provenance.
- [ ] Add bounded live buffering, explicit backpressure/drop metrics and reconnect behavior.
- [ ] Support ROS bag import and validate one real sensor recording end-to-end.

**Exit criteria:** a real recording can be ingested, inspected and replayed with traceable provenance. Offline MCAP ingestion is an initial increment; it does not complete the live ROS 2 integration milestone.

## Phase 4 — Inference and scientific validation

**Goal:** make the robustness workflow scientifically meaningful.

- [ ] Add a Python inference worker interface.
- [ ] Run a conventional detector and an SNN on identical input data.
- [ ] Use labelled data for precision, recall, mAP or task-appropriate metrics.
- [ ] Measure latency, memory and power when supported.
- [ ] Export experiment reports with dataset/model/configuration/commit provenance.

**Exit criteria:** at least one documented baseline comparison is reproducible and clearly separates measured values from estimates.

## Phase 5 — Web client and collaboration

**Goal:** enable browser-based team workflows.

- [ ] Build web client against the same versioned API.
- [ ] Add experiment registry, report review and team collaboration.
- [ ] Add identity, roles and organisation isolation.
- [ ] Add audit events and access controls.

**Exit criteria:** web, desktop and Android use the same experiment and device records.

## Phase 6 — Hybrid and cloud deployment

**Goal:** support labs and commercial customers with different data constraints.

- [ ] Package services with repeatable deployment manifests.
- [ ] Add backups, observability and operational monitoring.
- [ ] Support customer-controlled storage and local-only deployments.
- [ ] Add tenant provisioning, quotas and subscription entitlements only after customer validation.

**Exit criteria:** a documented deployment can be installed, upgraded, backed up and restored.

## Safety gate — physical control

Remote actuation is not part of the initial platform. Any future command channel requires a separate threat model, authenticated command protocol, robot-side limits, watchdog, fail-safe behavior, physical emergency-stop circuit and independent safety testing.

## Product validation alongside engineering

- Interview at least five potential research or industry users.
- Recruit two pilot teams before building broad enterprise features.
- Measure experiment reproduction time, scenario coverage, usability and willingness to pay.
- Keep synthetic demonstration results separate from validated real-world outcomes.
