# NRC Architecture

This document describes the current Kotlin application implementation. The target multi-client platform architecture is documented separately in [PLATFORM_ARCHITECTURE.md](PLATFORM_ARCHITECTURE.md).

## Current application

```text
                    +-----------------------------+
                    |       Android / Desktop     |
                    |      Compose Multiplatform  |
                    +--------------+--------------+
                                   |
                    +--------------v--------------+
                    |       composeApp             |
                    | UI + Domain + StateFlow      |
                    +---+----------+-------------+
                        |          |
             +----------v---+   +--v------------------+
             | Simulator    |   | Mission Recorder    |
             | Telemetry    |   | + Deterministic     |
             | Source       |   | Replay              |
             +--------------+   +---------------------+
                        |
             +----------v----------------------------+
             | Environment Robustness Lab            |
             | Scenario profiles + deterministic     |
             | sensor perturbation + result report   |
             +----------------------------------------+
```

## Architectural principles

1. **Simulation first:** screens and experiment workflows can be exercised without hardware.
2. **Transport independence:** UI and domain models do not depend on ROS 2 libraries.
3. **Shared Kotlin:** state, validation, models, evaluation logic and presentation are shared across targets.
4. **Observable pipeline:** preserve timestamps, sequence numbers, sensor identity and source metadata.
5. **Evidence before claims:** clearly distinguish simulated estimates from measured sensor/model results.
6. **Safety separation:** UI guards are convenience controls; physical safety is enforced by the robot/controller.
7. **Deterministic replay:** recorded inputs are replayed in timestamp order for repeatable analysis.
8. **Bounded operation:** in-memory recording has an explicit frame cap to avoid unbounded memory growth.

## Current data flow

`DemoTelemetryEngine` generates a 10 Hz `RobotTelemetry` stream and exposes it through `StateFlow`. The shared Compose UI observes the stream. When mission capture is enabled, snapshots are passed to `MissionRecorder`, which stores bounded `RecordedTelemetryFrame` entries with offsets relative to the start of capture. Replay publishes those snapshots back into the same telemetry state flow while the simulator loop is paused.

The `RobustnessLabEngine` applies a selected `EnvironmentScenario` to a snapshot using `RobustnessEvaluator`. The evaluator creates a deterministic stressed copy of the telemetry, then produces a `StressTestReport` with sensor retention, event-noise, detection-confidence and inference-latency comparisons. The dashboard renders the stressed sensor snapshot separately from the live simulator telemetry.

The current stress coefficients are illustrative. They are not calibrated atmospheric or sensor-physics models, and their confidence values are not ground-truth accuracy metrics.

## Current telemetry contract

The initial `RobotTelemetry` model contains:

- Sequence and timestamp.
- Connection and mission state.
- Battery, linear/angular velocity and pose.
- CPU load, inference latency and event rate.
- Event-camera samples with polarity.
- LiDAR polar points.
- IMU acceleration and angular velocity.
- Perception detections with confidence and bearing.
- SNN layer activity, active-neuron count, firing rate and mean membrane potential.
- Target speed and emergency-stop state.

The versioned wire contract is defined in [schemas/telemetry-envelope.v1.schema.json](../schemas/telemetry-envelope.v1.schema.json).

## Current code layout

- `composeApp`: shared KMP library, common UI and current domain/application logic, plus the JVM desktop entry point.
- `androidApp`: Android-specific application shell that consumes the shared module.
- `commonMain`: domain models, simulator, safety logic, mission recording, robustness evaluator and Compose UI.
- `commonTest`: platform-independent tests for safety, mission recording, telemetry validation and robustness evaluation.

## Planned platform boundaries

NRC will evolve toward a separately testable KMP core, a Kotlin/Ktor platform API, a read-only ROS 2 edge gateway, Python inference workers and Android/desktop/web clients. These components are not present yet. See [PLATFORM_ARCHITECTURE.md](PLATFORM_ARCHITECTURE.md) and [PLATFORM_ROADMAP.md](PLATFORM_ROADMAP.md).

The first edge gateway is read-only. Any future motion-command channel requires a separate safety architecture and independent validation.
