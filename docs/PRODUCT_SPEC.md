# NRC Product Specification

## Product statement

Neuromorphic Robotics Console (NRC) is an evaluation and evidence platform for autonomous perception under adverse conditions. It combines multimodal sensor observability, neuromorphic model analysis, reproducible stress experiments and failure replay.

NRC is not intended to replace general-purpose robotics dashboards, fleet-management platforms or high-fidelity simulators. It integrates with those systems and focuses on repeatable perception validation and evidence.

## User problem

Robotics teams often have separate tools for sensor visualization, simulation, model benchmarking, robot operations and experiment records. They need a repeatable way to connect environmental conditions, sensor quality, model outputs, robot decisions and failures.

## Target users

- University robotics and neuromorphic research laboratories.
- Autonomous systems and event-vision startups.
- ADAS and autonomous mobility research teams.
- UAV, agricultural and disaster-response robotics developers.
- Industrial robotics integrators.

## Core workflow

1. Connect to a robot, simulator or recorded dataset.
2. Inspect synchronized sensor streams and robot telemetry.
3. Define an environmental condition or load a recorded scenario.
4. Run conventional and neuromorphic perception models against the same input.
5. Compare quality, latency, memory, power and robustness metrics.
6. Trace model outputs and robot decisions over a shared timeline.
7. Record, replay and export a reproducible experiment report.

## Current prototype

- Kotlin Multiplatform shared application for Android and desktop.
- Hardware-free 10 Hz robot telemetry simulator.
- Event-camera and 2D LiDAR visualisation.
- IMU, pose, detection and SNN activity telemetry.
- Simulated speed guard and emergency-stop state.
- Bounded in-memory mission recording and timestamped replay.
- Versioned telemetry envelope and sequence/schema validation.
- Environment Robustness Lab with clear, rain, fog, low-light and dust/smoke profiles.
- Deterministic synthetic perturbations for LiDAR returns, event samples, detection confidence and inference latency.

**Prototype limitation:** the current environment profiles are illustrative coefficients. They are not calibrated weather physics, measured model accuracy or safety certification.

## Differentiated product capabilities

1. Adverse-environment experiment design and repeatable scenario execution.
2. Synchronized sensor-to-perception-to-action traceability.
3. Conventional versus spiking neural network comparisons on identical inputs.
4. Ground-truth-aware evaluation with reproducible dataset/model/configuration provenance.
5. Edge efficiency benchmarking including latency, memory, power and energy per inference.
6. Failure reproduction and comparison across model versions.

## Integration strategy

- ROS 2 for live robot middleware.
- MCAP and ROS bag for recorded sensor data.
- CARLA and NVIDIA Isaac Sim for simulation sources.
- Public event-vision and adverse-weather datasets for repeatable benchmark suites.
- Hardware-specific telemetry for edge and neuromorphic energy measurements.

## Next milestones

1. Complete the environment robustness prototype and its automated tests.
2. Verify Android and desktop builds.
3. Add a versioned experiment manifest and report export.
4. Implement a read-only WebSocket telemetry adapter.
5. Build a separate ROS 2 gateway.
6. Ingest real adverse-weather/event-vision recordings.
7. Compare one conventional detector and one SNN on identical labelled inputs.
8. Add hardware-specific power and latency instrumentation.

## Success criteria

- A judge can launch and run the simulator without hardware or API credentials.
- Environmental stress profiles produce deterministic and testable outputs.
- Every report identifies whether data is simulated or measured.
- A recorded experiment can be reproduced from its inputs and configuration.
- Model comparisons use identical data and documented evaluation metrics.
- The platform separates simulator, transport, domain logic, evaluation and presentation.
- No physical robot command is enabled without an independent safety review.

## Explicitly out of scope for the first release

- Autonomous navigation or robot decision-making inside NRC.
- Replacing ROS 2, CARLA, Isaac Sim or robot firmware.
- Certified safety control.
- Cloud fleet management and multi-tenant enterprise administration.
