# NRC — Neuromorphic Robotics Console

**NRC is a multi-platform evaluation and evidence platform for autonomous perception under adverse conditions.**

It connects sensor observability, event-based perception, neuromorphic benchmarking, controlled environmental stress testing and reproducible experiment analysis.

## Product direction

NRC is being architected as one platform with shared experiment contracts and multiple clients:

- **Android:** field monitoring, alerts and experiment review.
- **Desktop:** engineering console, sensor visualisation, replay and analysis.
- **Web:** team collaboration, experiment registry and report review.
- **Shared core:** common domain models, validation, scenario logic and experiment metrics.
- **Platform API:** identity, device registry, experiments, reports and live telemetry.
- **Edge gateway:** read-only ROS 2, MCAP and ROS bag connectivity.
- **Inference runtime:** separate workers for conventional and spiking neural networks.

The target architecture is staged. A planned component is not considered implemented until it exists, builds and passes tests.

## Current prototype

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

**Important:** current robustness profiles are illustrative synthetic perturbations. They are not calibrated weather physics, measured model accuracy or safety certification. The API backend, web client, ROS 2 gateway and inference workers are not yet implemented.

## Architecture and engineering plan

- [Platform architecture](docs/PLATFORM_ARCHITECTURE.md)
- [Platform implementation roadmap](docs/PLATFORM_ROADMAP.md)
- [ADR-001: Multi-client platform](docs/ADR/ADR-001-platform-boundaries.md)
- [ADR-002: Versioned data contracts](docs/ADR/ADR-002-data-contracts.md)
- [Product specification](docs/PRODUCT_SPEC.md)
- [Current application architecture](docs/ARCHITECTURE.md)
- [Environment Robustness Lab](docs/ROBUSTNESS_LAB.md)
- [Market positioning](docs/MARKET_POSITIONING.md)
- [Social impact and business model](docs/IMPACT_AND_BUSINESS_MODEL.md)
- [Start guide](START_HERE.md)

## Versioned contracts

- [Telemetry envelope v1](schemas/telemetry-envelope.v1.schema.json)
- [Experiment manifest v1](schemas/experiment-manifest.v1.schema.json)

## Safety

The current application is simulator-only. NRC is not a safety-rated controller. The first platform release is read-only: physical robot commands remain disabled until independent controller-side limits, authentication, watchdogs and hardware emergency-stop requirements are implemented and independently tested.
