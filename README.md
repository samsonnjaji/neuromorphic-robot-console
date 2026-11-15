# NRC — Neuromorphic Robotics Console

**NRC is an evaluation and evidence platform for autonomous perception under adverse conditions.**

It brings sensor observability, event-based perception, neuromorphic benchmarking, controlled environmental stress testing and reproducible experiment analysis into one workflow.

## Why NRC?

Robotics teams already have capable tools for visualization, simulation and fleet operations. NRC is not intended to replace those tools. Its focus is the evidence loop between environmental conditions, sensor quality, model behavior, robot decisions and reproducible failures.

## Current prototype

- Kotlin Multiplatform shared application for Android and desktop.
- Hardware-free robot telemetry simulator.
- Event-camera and 2D LiDAR views.
- IMU, pose, detection and SNN activity telemetry.
- Simulated speed guard and emergency-stop state.
- Bounded mission recording and timestamped replay.
- Versioned telemetry envelope and stream validation.
- Environment Robustness Lab with deterministic synthetic stress profiles.
- Versioned experiment manifest attached to each stress-test report, including source frame, model identifier and scenario coefficients.

**Important:** the current robustness profiles are illustrative synthetic perturbations. They are not calibrated weather physics, measured model accuracy or safety certification. Experiment manifests currently describe simulator runs; persistent export and real dataset provenance are future work.

## Product direction

1. Import real ROS 2, MCAP and ROS bag recordings.
2. Add validated adverse-weather and event-vision datasets.
3. Compare conventional and spiking models on identical inputs.
4. Capture ground-truth-aware perception metrics.
5. Measure edge latency, memory, power and energy per inference.
6. Serialize reproducible reports with dataset/model/configuration provenance.

## Quick start

See [START_HERE.md](START_HERE.md) for setup and run instructions.

## Engineering documentation

- [Product specification](docs/PRODUCT_SPEC.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Safety model](docs/SAFETY_MODEL.md)
- [Telemetry protocol](docs/TELEMETRY_PROTOCOL.md)
- [Environment Robustness Lab](docs/ROBUSTNESS_LAB.md)
- [Market positioning](docs/MARKET_POSITIONING.md)
- [Social impact and business model](docs/IMPACT_AND_BUSINESS_MODEL.md)

## Safety

The current application is simulator-only. NRC is not a safety-rated controller. Physical robot commands remain disabled until independent controller-side limits, authentication, watchdogs and hardware emergency-stop requirements are implemented and tested.
