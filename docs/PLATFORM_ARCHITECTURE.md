# NRC Platform Architecture

**Status:** Target architecture, accepted and being implemented in validated stages  
**Current baseline:** Kotlin Multiplatform core, Android/desktop simulator clients, Ktor API MVP, PostgreSQL experiment/run/recording metadata and validated local desktop relay  
**Architecture principle:** one shared robotics intelligence and experiment model, multiple clients and deployment modes.

## 1. Product boundary

NRC is an evaluation, observability and evidence platform for autonomous perception under adverse conditions. It connects sensor data, environmental test conditions, perception models, robot state, failure traces and reproducible experiment reports.

NRC is not a replacement for ROS 2, robot firmware, Foxglove, CARLA, Isaac Sim or specialist model frameworks. It integrates with these systems and focuses on the evaluation workflow between them.

## 2. Target platform architecture

```text
     ANDROID CLIENT       DESKTOP CLIENT       WEB CLIENT
     Compose/Kotlin       Compose/Kotlin       Web/TypeScript
             \                 |                  /
              \                |                 /
               +---------------+----------------+
                               |
                     NRC VERSIONED API
                  REST + WebSocket / HTTPS
                               |
              +----------------+-----------------+
              |                                  |
        NRC PLATFORM API                  EXPERIMENT SERVICE
        identity, teams,                  orchestration,
        devices, access,                  evaluation runs,
        metadata, audit                   manifests, reports
              |                                  |
              +----------------+-----------------+
                               |
                     NRC SHARED CORE
               models, validation, metrics,
              scenario engine, replay logic
                               |
           +-------------------+-------------------+
           |                   |                   |
       ROBOT EDGE          INFERENCE           DATA / STORAGE
       ROS 2 gateway       Python workers      PostgreSQL
       MCAP/ROS bag         PyTorch/ONNX/SNN    Object storage
       Sensor adapters     Neuromorphic SDKs   Dataset registry
           |                   |                   |
           +-------------------+-------------------+
                               |
              ROBOTS / SIMULATORS / DATASETS
         Event camera, LiDAR, RGB, radar, IMU,
          CARLA, Isaac Sim, field recordings
```

The diagram is the target architecture, not a claim that all services already exist.

## 3. Architectural components

### A. NRC Core — shared intelligence and experiment logic

**Implementation:** Kotlin Multiplatform.

Responsibilities:
- Canonical domain models and versioned experiment definitions.
- Telemetry validation and sequence/timestamp handling.
- Environment scenario definitions and experiment evaluation.
- Deterministic replay and comparison logic.
- Metric definitions and report models.
- Client SDK and shared application state where appropriate.

Rules:
- No direct ROS 2 dependency in shared core.
- No database or cloud-vendor dependency in domain logic.
- No UI dependency in the core module.
- Keep source timestamps, sequence IDs, model identity and dataset provenance.
- Separate measured values from synthetic or estimated values.

**Current state:** `core/nrc-core` contains shared telemetry/domain models, stream validation, safety-envelope logic, mission recording, robustness evaluation and transport interfaces. It has no Compose, database, network-client or ROS 2 dependency. Core JVM tests pass in CI.

The existing `composeApp` remains the Compose presentation/application module and currently contains the demo telemetry source and dashboard wiring.

### B. NRC Platform API — shared backend

**Implementation:** Kotlin/JVM with Ktor.

**Implemented local MVP:**
- `GET /api/v1/health`.
- `GET /api/v1/experiments`.
- `POST /api/v1/experiments`.
- `GET /api/v1/experiments/{experimentId}`.
- `POST /api/v1/robots/{robotId}/telemetry` for versioned telemetry ingestion.
- `WS /api/v1/robots/{robotId}/stream` for live telemetry fan-out.
- Typed request validation, sequence validation and structured error responses.
- In-memory repository for tests and no-database development.
- PostgreSQL repositories for durable experiment definitions, experiment runs and recording-session metadata, using HikariCP and Flyway migrations.
- Experiment runs retain a source revision, JSON configuration snapshot, lifecycle timestamps, result summary and failure reason.
- Recording sessions retain format, source identity, metadata, sample count and lifecycle timestamps. The current implementation registers metadata only; it does not upload or retain recording binaries.
- Local PostgreSQL service definition in `deploy/local/compose.yaml`.

The clients must not connect directly to databases or inference workers.

**Not implemented yet:** user/device authentication, organisation/tenant isolation, audit events, PostgreSQL-backed telemetry history, recording uploads, object storage, rate limits and production deployment hardening. Do not expose this unauthenticated MVP to a public network.

### C. NRC Edge Gateway — robot connectivity

**Planned implementation:** separate ROS 2 process, initially Python or C++ according to driver and deployment needs.

Responsibilities:
- Subscribe to configured ROS 2 sensor and state topics.
- Normalize timestamps, frame IDs, units and source metadata.
- Publish read-only telemetry to NRC.
- Import MCAP and ROS bag recordings.
- Buffer data when the network is unavailable.
- Report device identity, connection state and software version.

Initial integrations: ROS 2, MCAP, ROS bag, event cameras, LiDAR, RGB cameras, radar and IMU.

The first gateway is read-only. Motion commands and remote actuation are explicitly outside the initial release.

### D. NRC Inference Runtime — model execution

**Planned implementation:** separate Python service.

Responsibilities:
- Load conventional and spiking perception models.
- Run models against the same recorded input for fair comparison.
- Return detections, embeddings or task outputs with timestamps and model provenance.
- Measure wall-clock inference latency and runtime resource use.
- Provide hardware-specific measurement adapters when supported.

Potential runtimes: PyTorch, ONNX Runtime, SNN frameworks and hardware-specific neuromorphic SDKs. These are integration choices to validate, not all committed dependencies.

### E. Clients — purpose-specific interfaces

| Client | Primary users | Main responsibility |
|---|---|---|
| Android | Field researchers and robot operators | Monitoring, alerts, experiment status, review |
| Desktop | Robotics engineers and researchers | Sensor visualisation, replay, analysis, configuration |
| Web | Labs, teams and administrators | Collaboration, experiment registry, reports, access management |

Android and desktop currently share a Kotlin Multiplatform Compose codebase. The web client is a planned client of the same versioned API. It will not have a separate backend or a second source of experiment truth.

## 4. Data and storage architecture

- **PostgreSQL:** currently stores experiment definitions, experiment-run metadata and recording-session metadata. Target scope also includes users, organisations, device registry, manifests, model/dataset metadata, report indexes and audit events.
- **S3-compatible object storage:** planned for large MCAP/ROS bag recordings, image/video sequences, benchmark artifacts and exported reports.
- **Local edge buffer:** planned for temporary recording chunks and queued telemetry when offline.
- **Client cache:** limited recent state and metadata; never the canonical experiment record.

Do not place high-volume raw event-camera streams into ordinary relational rows or push unbounded event payloads through a dashboard WebSocket. Use recording/chunk upload and object storage for large artifacts; stream compact telemetry summaries or bounded windows.

The current local development setup starts PostgreSQL with Docker Compose. API migration runs automatically at startup when `NRC_DATABASE_URL` is configured.

## 5. API and integration boundaries

### API conventions

- Prefix public API routes with `/api/v1`.
- Use HTTPS for REST and WSS for WebSocket outside local development.
- Use versioned JSON schemas for control-plane metadata and compact live messages.
- Include robot/device ID, schema version, sequence, source timestamp and source identity.
- Preserve units and coordinate-frame metadata.
- Reject unknown or unsupported schema versions safely.
- Use chunked or object-storage uploads for large sensor recordings.
- Never expose database credentials or inference-worker credentials to clients.

### Implemented API routes

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/health` | Service health |
| `GET /api/v1/experiments` | List recent experiment metadata |
| `POST /api/v1/experiments` | Create experiment metadata |
| `GET /api/v1/experiments/{experimentId}` | Retrieve experiment metadata |
| `POST /api/v1/experiments/{experimentId}/runs` | Create an experiment run |
| `GET /api/v1/experiments/{experimentId}/runs` | List experiment runs |
| `GET /api/v1/runs/{runId}` | Retrieve an experiment run |
| `POST /api/v1/runs/{runId}/transition` | Advance a run through validated lifecycle states |
| `POST /api/v1/runs/{runId}/recordings` | Register a recording session |
| `GET /api/v1/runs/{runId}/recordings` | List recording sessions for a run |
| `GET /api/v1/recordings/{recordingId}` | Retrieve recording-session metadata |
| `POST /api/v1/recordings/{recordingId}/transition` | Advance a recording-session lifecycle state |
| `POST /api/v1/robots/{robotId}/telemetry` | Validate and ingest a telemetry snapshot |
| `WS /api/v1/robots/{robotId}/stream` | Subscribe to live telemetry snapshots |

### Planned API routes

Robot registry, authenticated users/organisations, artifact registration and S3-compatible recording upload coordination, model registry and dataset registry will be added in subsequent milestones.

### Versioned contracts

The v1 telemetry envelope and experiment manifest are stored under `schemas/`. Positive and negative fixtures are checked by `scripts/validate_schemas.py` in GitHub Actions. The Ktor API also decodes the typed Kotlin serialization envelope and applies stream/schema-version/sequence checks. The JSON Schema documents remain the cross-language wire-contract reference.

## 6. Deployment modes

### Local laboratory mode
A lab runs NRC API, PostgreSQL, object storage and model workers on a workstation or local server. Robot data can remain on the lab network. Internet access is not required for the core workflow once dependencies and models are available.

### Hybrid mode
The edge gateway and raw recordings remain on-site. Experiment metadata and selected reports synchronize to a central service.

### Cloud-managed mode
The API, database and object storage are hosted centrally for teams that need collaboration across locations. Large raw recordings can remain in customer-controlled storage where required.

All three modes use the same experiment contracts and client-facing API.

## 7. Security and safety requirements

- OIDC/OAuth2-compatible user authentication.
- Role-based access control and organisation/tenant isolation.
- Per-device credentials with revocation and rotation.
- TLS in transit and encryption at rest.
- Audit logs for authentication, data access and experiment actions.
- Secrets supplied through environment or secret-management systems, never committed.
- Least-privilege service-to-service access.
- Dataset consent, retention and access controls where personal or sensitive data is present.
- No remote actuation in the first release.

Any future control capability requires a separate threat model, authenticated command protocol, robot-side speed and geofence limits, watchdog, fail-safe state, physical emergency-stop circuit and independent validation. A user-interface emergency-stop button is not a certified safety function.

## 8. Repository layout

```text
neuromorphic-robot-console/
├── core/nrc-core/                 # Kotlin Multiplatform domain/experiment SDK
├── composeApp/                    # Existing shared Compose presentation + desktop
├── androidApp/                    # Existing Android application shell
├── services/platform-api/         # Ktor API + PostgreSQL repository
├── deploy/local/                  # Local PostgreSQL Compose setup
├── schemas/                       # Versioned JSON contracts and fixtures
├── scripts/                       # Schema validation
├── clients/web/                   # Browser client (planned)
├── services/inference-runtime/    # Python model execution workers (planned)
├── edge/ros2-gateway/              # Read-only ROS 2 adapter (planned)
└── docs/                           # Architecture, ADRs, roadmap and handoff
```

## 9. Quality gates

1. Android and desktop builds remain green.
2. Domain and experiment tests run without physical robot hardware.
3. API schemas are versioned and validated.
4. Simulation data is visibly distinguished from measured data.
5. Experiments retain dataset, source, model, configuration and software provenance.
6. Large recordings are handled as files/chunks, not unbounded UI messages.
7. No actuation path exists in the initial release.
8. Local-only deployment remains possible.
9. PostgreSQL persistence and migration behavior are integration-tested.
10. No local Cursor test handoff occurs until CI is green and local run instructions are committed.

## 10. Current implementation versus target

| Capability | Status |
|---|---|
| Independent Kotlin Multiplatform `nrc-core` module | Implemented |
| Android/desktop Compose clients | Implemented foundation |
| Simulator, sensor views and mission replay | Implemented |
| Synthetic Environment Robustness Lab | Implemented |
| In-memory experiment provenance manifest | Implemented |
| Telemetry and manifest JSON schemas + CI fixtures | Implemented |
| Ktor API health/experiment/telemetry routes | Implemented MVP |
| PostgreSQL experiment/run/recording metadata repositories + Flyway migrations | Implemented; CI verified |
| Experiment-run lifecycle API and state validation | Implemented; CI verified |
| Recording-session metadata lifecycle API | Implemented; CI verified |
| Local PostgreSQL Docker Compose setup | Implemented |
| Authentication and organisation access controls | Planned |
| S3-compatible recording storage | Planned |
| ROS 2 edge gateway | Planned |
| Python inference workers | Planned |
| Web client | Planned |
| Real dataset evaluation and measured model comparisons | Planned |
| Cloud/hybrid deployment | Planned |

**Product rule:** NRC is a platform, but it must be delivered in validated increments. Do not claim a planned service is available until it exists, builds and passes its tests.
