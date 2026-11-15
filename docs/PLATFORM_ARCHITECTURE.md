# NRC Platform Architecture

**Status:** Target architecture, approved for staged implementation  
**Current baseline:** Kotlin Multiplatform Android + desktop simulator  
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

**Preferred implementation:** Kotlin Multiplatform.

Responsibilities:
- Canonical domain models and versioned experiment definitions.
- Telemetry validation and sequence/timestamp handling.
- Environment scenario definitions and experiment orchestration.
- Deterministic replay and comparison logic.
- Metric definitions and report models.
- Client SDK and shared application state where appropriate.

Rules:
- No direct ROS 2 dependency in shared core.
- No database or cloud-vendor dependency in domain logic.
- No UI dependency in the eventual extracted core module.
- Keep source timestamps, sequence IDs, model identity and dataset provenance.
- Separate measured values from synthetic or estimated values.

**Current state:** these responsibilities are partly implemented in `composeApp`. The domain and presentation code have not yet been physically separated into independent Gradle modules. That extraction is a planned refactor, not completed work.

### B. NRC Platform API — shared backend

**Preferred implementation:** Kotlin/JVM with Ktor.

Responsibilities:
- Versioned REST API for users, organisations, devices, experiments and reports.
- WebSocket endpoints for live telemetry and experiment status.
- Authentication and role-based authorisation.
- Device registration and scoped device credentials.
- Experiment metadata and manifest persistence.
- Audit events and tenant boundaries.
- Upload/download coordination for large recordings.

The backend is the shared entry point for Android, desktop and web clients. The clients must not connect directly to the database or inference workers.

### C. NRC Edge Gateway — robot connectivity

**Preferred implementation:** a separate ROS 2 process, initially Python or C++ according to driver and deployment needs.

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

**Preferred implementation:** a separate Python service.

Responsibilities:
- Load conventional and spiking perception models.
- Run models against the same recorded input for fair comparison.
- Return detections, embeddings or task outputs with timestamps and model provenance.
- Measure wall-clock inference latency and runtime resource use.
- Provide hardware-specific measurement adapters when supported.

Potential runtimes: PyTorch, ONNX Runtime, SNN frameworks and hardware-specific neuromorphic SDKs. These are integration choices to validate, not all committed dependencies.

The Kotlin core orchestrates and compares experiments; it does not need to reimplement every ML framework.

### E. Clients — purpose-specific interfaces

| Client | Primary users | Main responsibility |
|---|---|---|
| Android | Field researchers and robot operators | Monitoring, alerts, experiment status, review |
| Desktop | Robotics engineers and researchers | Sensor visualisation, replay, analysis, configuration |
| Web | Labs, teams and administrators | Collaboration, experiment registry, reports, access management |

Android and desktop currently share a Kotlin Multiplatform Compose codebase. The web client is a planned client of the same versioned API. It will not have a separate backend or a second source of experiment truth.

## 4. Data and storage architecture

Use the right storage for each data type:

- **PostgreSQL:** users, organisations, device registry, experiment manifests, model/dataset metadata, report indexes and audit events.
- **S3-compatible object storage:** large MCAP/ROS bag recordings, image/video sequences, benchmark artifacts and exported reports.
- **Local edge buffer:** temporary recording chunks and queued telemetry when offline.
- **Client cache:** limited recent state and metadata; never the canonical experiment record.

Do not place high-volume raw event-camera streams into ordinary relational rows or push unbounded event payloads through a dashboard WebSocket. Use recording/chunk upload and stream compact telemetry summaries or bounded windows.

The first local development deployment can use Docker Compose with PostgreSQL and a local S3-compatible object store. Cloud hosting is optional, not required for the competition demo.

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

### Initial endpoint proposal

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/health` | Service health |
| `GET /api/v1/robots` | Robots visible to the authenticated user |
| `GET /api/v1/robots/{robotId}` | Robot metadata and status |
| `GET /api/v1/robots/{robotId}/stream` | WebSocket telemetry stream |
| `POST /api/v1/experiments` | Create an experiment |
| `GET /api/v1/experiments/{experimentId}` | Read experiment and manifest |
| `POST /api/v1/experiments/{experimentId}/runs` | Request an evaluation run |
| `GET /api/v1/experiments/{experimentId}/reports` | List reports |
| `POST /api/v1/recordings/uploads` | Start a recording upload |

These are proposed v1 routes; the backend has not been implemented.

## 6. Deployment modes

### Local laboratory mode
A lab runs NRC API, database, object storage and model workers on a workstation or local server. Robot data can remain on the lab network. Internet access is not required for the core workflow once dependencies and models are available.

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

Any future control capability requires a separate threat model, authenticated command protocol, robot-side speed and geofence limits, watchdog, physical emergency-stop circuit, fail-safe state and independent validation. A user-interface emergency-stop button is not a certified safety function.

## 8. Repository evolution

The repository should evolve toward the following logical structure. This is a target layout; do not move existing source files in a large unreviewed change.

```text
neuromorphic-robot-console/
├── core/
│   └── nrc-core/                 # Kotlin Multiplatform domain/experiment SDK
├── clients/
│   ├── android/                  # Android application
│   ├── desktop/                  # Desktop engineering console
│   └── web/                      # Browser client
├── services/
│   ├── platform-api/             # Ktor REST/WebSocket backend
│   └── inference-runtime/        # Python model execution workers
├── edge/
│   └── ros2-gateway/             # Read-only ROS 2 and recording adapter
├── schemas/
│   ├── telemetry-envelope.v1.schema.json
│   └── experiment-manifest.v1.schema.json
├── deploy/
│   ├── local/
│   └── cloud/
├── docs/
│   ├── ADR/
│   └── ...
└── scripts/
```

The existing `composeApp` and `androidApp` remain the working clients during migration. Extract the pure domain and experiment logic before introducing cross-module dependencies.

## 9. Quality gates

Every milestone must preserve these conditions:

1. Android and desktop builds remain green.
2. Domain and experiment tests run without physical robot hardware.
3. API schemas are versioned and validated.
4. Simulation data is visibly distinguished from measured data.
5. Experiments retain dataset, source, model, configuration and software provenance.
6. Large recordings are handled as files/chunks, not unbounded UI messages.
7. No actuation path exists in the initial release.
8. Local-only deployment remains possible.

## 10. Current implementation versus target

| Capability | Status |
|---|---|
| Kotlin Multiplatform Android/desktop foundation | Implemented |
| Simulator, sensor views and mission replay | Implemented |
| Synthetic Environment Robustness Lab | Implemented |
| In-memory experiment provenance manifest | Implemented |
| Shared domain extracted into independent core module | Planned |
| Ktor API and identity layer | Planned |
| PostgreSQL and object storage | Planned |
| ROS 2 edge gateway | Planned |
| Python inference workers | Planned |
| Web client | Planned |
| Real dataset evaluation and measured model comparisons | Planned |
| Cloud/hybrid deployment | Planned |

**Product rule:** NRC is a platform, but it must be delivered in validated increments. Do not claim a planned service is available until it exists, builds and passes its tests.
