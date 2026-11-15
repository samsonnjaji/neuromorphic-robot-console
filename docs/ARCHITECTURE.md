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
                    +------+----------+-----------+
                           |          |
                  +--------v--+   +---v----------------+
                  | Simulator |   | Mission Recorder   |
                  | Telemetry |   | / Deterministic    |
                  | Source    |   | Replay             |
                  +-----------+   +--------------------+
                           |
                    Future transport boundary
                           |
                    +------v------+
                    | ROS 2 Bridge|
                    | WebSocket   |
                    +------+------+
                           |
               +-----------v------------------+
               | Robot / Sensors / Perception |
               | Event camera, LiDAR, IMU,    |
               | odometry, SNN outputs        |
               +------------------------------+
```

## Architectural principles

1. **Simulation first:** all screens can be exercised without hardware.
2. **Transport independence:** UI and domain models do not depend on ROS 2 libraries.
3. **Shared Kotlin:** state, validation, models, mission capture and presentation are shared across targets.
4. **Observable pipeline:** preserve timestamps, sequence numbers and source metadata.
5. **Safety separation:** UI guards are convenience controls; physical safety is enforced by the robot/controller.
6. **Deterministic replay:** recorded inputs are replayed in timestamp order for repeatable analysis.
7. **Bounded operation:** in-memory recording has an explicit frame cap to avoid unbounded memory growth.

## Current data flow

`DemoTelemetryEngine` generates a 10 Hz `RobotTelemetry` stream and exposes it through `StateFlow`. The shared Compose UI observes the stream. When mission capture is enabled, snapshots are passed to `MissionRecorder`, which stores bounded `RecordedTelemetryFrame` entries with offsets relative to the start of capture. Replay publishes those snapshots back into the same telemetry state flow while the simulator loop is paused.

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

## Planned ROS 2 integration

NRC will not embed ROS 2 middleware directly in the shared Kotlin module. A small gateway process will subscribe to selected ROS 2 topics and publish a versioned JSON or binary telemetry stream over WebSocket. Commands will flow in the opposite direction through an allowlisted command API. The gateway will validate schema, timestamps, command ranges and authentication; the robot controller will independently enforce motion limits and watchdog behaviour.

Candidate ROS 2 topics include `/scan`, `/imu/data`, `/odom`, and custom event-camera/perception topics. `/cmd_vel` integration is explicitly disabled until the safety and command-authentication design is complete. Topic names will be configurable rather than hard-coded.

## Platform architecture

- `composeApp`: shared KMP library plus the JVM desktop entry point.
- `androidApp`: Android-specific application shell that consumes the shared module.
- `commonMain`: domain models, simulator, safety logic, mission recording and Compose UI.
- `commonTest`: platform-independent tests for safety and mission recording.
