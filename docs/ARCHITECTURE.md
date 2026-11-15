# NRC Architecture

## High-level design

```text
                    +-----------------------------+
                    |     Android App / Desktop   |
                    |      Compose Multiplatform  |
                    +--------------+--------------+
                                   |
                    +--------------v--------------+
                    |     Shared NRC Application  |
                    | UI + Domain + StateFlow     |
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
             +----------+----------------------------+
                        |
             Future transport / dataset adapters
                        |
             +----------v----------------------------+
             | ROS 2 / MCAP / ROS bag / Simulators    |
             | Event camera, LiDAR, IMU, RGB, SNN     |
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

## Telemetry contract

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

## Planned data and transport adapters

NRC will not embed ROS 2 middleware directly in the shared Kotlin module. A separate gateway will subscribe to selected ROS 2 topics and publish a versioned JSON or binary telemetry stream over WebSocket. Dataset adapters will ingest recorded MCAP/ROS bag files and preserve source timestamps and provenance.

Candidate ROS 2 topics include `/scan`, `/imu/data`, `/odom`, and custom event-camera/perception topics. Topic names will be configurable rather than hard-coded.

The initial gateway is read-only. `/cmd_vel` integration remains disabled until the command-security design, robot-side limits, watchdog and physical emergency-stop requirements are reviewed and tested.

## Platform architecture

- `composeApp`: shared KMP library plus the JVM desktop entry point.
- `androidApp`: Android-specific application shell that consumes the shared module.
- `commonMain`: domain models, simulator, safety logic, mission recording, robustness evaluator and Compose UI.
- `commonTest`: platform-independent tests for safety, mission recording, telemetry validation and robustness evaluation.
