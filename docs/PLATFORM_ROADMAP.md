# NRC Platform Implementation Roadmap

This roadmap sequences platform work so that every phase produces a usable, testable increment. Competition success is not a dependency for continuing the product.

## Phase 0 — Platform foundation

**Goal:** agree on boundaries before adding services.

- [x] Document multi-client platform architecture.
- [x] Record architecture decisions for platform boundaries and data contracts.
- [x] Define initial telemetry and experiment JSON schemas.
- [x] Add positive and negative schema fixtures and CI validation.
- [ ] Add a versioned OpenAPI document for the first REST endpoints.
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

**Exit criteria:** core tests run without UI, network, database or robot dependencies. **Passed in CI** for commit `d98a28d`.

## Phase 2 — Local Platform API

**Goal:** make the applications clients of a real backend.

- [ ] Create a Ktor service with `/api/v1/health`.
- [ ] Add experiment create/read/list endpoints.
- [ ] Add WebSocket telemetry ingestion and fan-out.
- [ ] Add request validation and structured errors.
- [ ] Add PostgreSQL for metadata and migrations.
- [ ] Add local object storage for recordings.
- [ ] Add local Docker Compose deployment and integration tests.

**Exit criteria:** the desktop client can connect to the local API and read live simulator telemetry and experiment records.

## Phase 3 — Edge and real data

**Goal:** connect real robotics data without enabling control.

- [ ] Implement a read-only ROS 2 gateway.
- [ ] Support MCAP and ROS bag import.
- [ ] Preserve source timestamps, frame IDs, units and provenance.
- [ ] Add buffering and reconnect behavior.
- [ ] Validate one real sensor recording end-to-end.

**Exit criteria:** a real recording can be ingested, inspected and replayed with traceable provenance.

## Phase 4 — Inference and scientific validation

**Goal:** make the robustness workflow scientifically meaningful.

- [ ] Add a Python inference worker interface.
- [ ] Run a conventional detector and an SNN on identical inputs.
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
