# NRC — Neuromorphic Robotics Console

**NRC is a multi-platform evaluation and evidence platform for autonomous perception under adverse conditions.**

It connects sensor observability, event-based perception, neuromorphic benchmarking, controlled environmental stress testing and reproducible experiment analysis.

## Product direction

NRC is one platform with shared experiment contracts and multiple clients:

- **Android:** field monitoring, alerts and experiment review.
- **Desktop:** engineering console, sensor visualisation, replay and analysis.
- **Web:** team collaboration, experiment registry and report review (planned).
- **Shared core:** common domain models, validation, scenario logic and experiment metrics.
- **Platform API:** experiment metadata, telemetry ingestion and live stream distribution.
- **Edge gateway:** Python offline MCAP inspection and recording-ingestion client v1; local API/RustFS end-to-end verification and live ROS 2 subscriptions remain outstanding.
- **Inference runtime:** separate workers for conventional and spiking neural networks (planned).

The target architecture is staged. A planned component is not considered implemented until it exists, builds and passes tests.

## Current prototype and platform foundation

- Independent Kotlin Multiplatform `nrc-core` module for shared domain and experiment logic.
- Kotlin Multiplatform Compose application foundation for Android and desktop.
- Hardware-free robot telemetry simulator.
- Event-camera and 2D LiDAR views.
- IMU, pose, detection and SNN activity telemetry.
- Simulated speed guard and emergency-stop state.
- Bounded mission recording and timestamped replay.
- Versioned telemetry envelope and stream validation.
- Environment Robustness Lab with deterministic synthetic stress profiles.
- Versioned in-memory experiment manifest attached to stress-test reports.
- Ktor Platform API MVP: health, experiment/run/recording lifecycle, telemetry ingestion and WebSocket fan-out.
- PostgreSQL repositories for experiment definitions, experiment runs, recording-session metadata and artifact metadata, using HikariCP and Flyway migrations.
- S3-compatible artifact storage with RustFS locally, presigned single-part/multipart uploads, and server-side size/SHA-256 verification.
- Python edge-gateway CLI for offline MCAP inspection, channel/schema/timestamp indexing, explicit source-origin labels, versioned provenance manifests, and verified artifact upload.
- Background cleanup for expired pending/failed uploads, including multipart aborts and retryable object deletion.
- Persistent experiment-run lifecycle with source-revision/configuration provenance and validated state transitions.
- Recording-session metadata lifecycle for MCAP, ROSBAG2, NRC_JSONL and other recording formats.
- Local PostgreSQL Docker Compose configuration.
- Desktop API-relay mode that publishes synthetic telemetry through the API and reads it back over WebSocket.
- OpenAPI 3.1 route documentation and JSON Schema contract validation in CI.
- Gradle 9.7.0 wrapper for reproducible local builds.

**Current limitations:** the API has no user/device authentication yet and binds to localhost by default. Artifact upload is not yet integrated into the desktop/Android user interface. The offline MCAP importer indexes recording metadata and uploads untouched MCAP files; it does not decode CDR payloads or create live sensor telemetry. Its manifest distinguishes physical-sensor, simulated, dataset and unknown origins from the recorded ingestion mode. Live ROS 2 subscriptions/decoders, inference workers and the web client remain planned. Background cleanup applies to expired pending/failed uploads; retention policies for verified artifacts are not implemented. Live telemetry fan-out is in memory. Desktop API-relay mode is synthetic and read-only; it is not a real robot connection. Current robustness profiles are illustrative synthetic perturbations, not calibrated weather physics, measured model accuracy or safety certification.

## Architecture and engineering plan

- [Platform architecture](docs/PLATFORM_ARCHITECTURE.md)
- [Platform implementation roadmap](docs/PLATFORM_ROADMAP.md)
- [Session handoff / next-chat context](docs/SESSION_HANDOFF.md)
- [Current application architecture](docs/ARCHITECTURE.md)
- [Platform API reference](docs/api/README.md)
- [Local PostgreSQL, RustFS object storage and desktop/API testing](deploy/local/README.md)
- [ADR-001: Multi-client platform](docs/ADR/ADR-001-platform-boundaries.md)
- [ADR-002: Versioned data contracts](docs/ADR/ADR-002-data-contracts.md)
- [Product specification](docs/PRODUCT_SPEC.md)
- [Environment Robustness Lab](docs/ROBUSTNESS_LAB.md)
- [Edge Gateway](docs/EDGE_GATEWAY.md)
- [Market positioning](docs/MARKET_POSITIONING.md)
- [Social impact and business model](docs/IMPACT_AND_BUSINESS_MODEL.md)
- [Start guide](START_HERE.md)

## Versioned contracts

- [Telemetry envelope v1](schemas/telemetry-envelope.v1.schema.json)
- [Experiment manifest v1](schemas/experiment-manifest.v1.schema.json)
- [Edge recording manifest v1](schemas/edge-recording-manifest.v1.schema.json)
- [OpenAPI 3.1](docs/api/openapi.json)

## Safety

The current application is simulator-first and NRC is not a safety-rated controller. The first platform release is read-only: physical robot commands remain disabled until a separate safety architecture, independent controller-side limits, authentication, watchdogs and hardware emergency-stop requirements are implemented and independently tested.
