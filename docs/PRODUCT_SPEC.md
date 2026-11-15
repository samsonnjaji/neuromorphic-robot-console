# NRC Product Specification

## Product statement

Neuromorphic Robotics Console (NRC) is a cross-platform observability, experiment-replay and safe-operation application for researchers and engineers developing autonomous, event-driven and neuromorphic robots.

## User problem

Robotics experiments often spread sensor streams, model outputs, robot state, safety status and experiment recordings across separate tools. This makes it difficult to understand perception-to-action behaviour in one place and to reproduce failures consistently.

## Target users

- Robotics researchers and graduate students.
- Autonomous systems engineers.
- Neuromorphic computing and event-vision researchers.
- University robotics laboratories and teaching teams.

## Core workflow

1. Connect to a robot or launch the built-in simulator.
2. Inspect robot health and telemetry.
3. Observe event-camera, LiDAR and IMU streams.
4. Inspect AI perception outputs and inference performance.
5. Issue constrained commands only when the connected robot authorises them.
6. Record and replay missions to analyse system behaviour.

## Current MVP

- Android and desktop clients sharing Kotlin domain logic and Compose UI.
- Deterministic 10 Hz simulated robot telemetry.
- Event-camera activity visualisation with positive/negative polarity.
- 2D LiDAR scan visualisation.
- IMU and pose telemetry.
- Detection list with confidence and bearing.
- Simulated SNN layer activity, active-neuron ratios, firing rates and mean membrane potentials.
- Inference latency, event-rate and CPU-load metrics.
- Simulated speed control, pause/resume and emergency-stop state.
- Bounded in-memory mission recording and timestamped replay.
- Shared safety and mission-recorder unit tests.

## Next milestones

1. Add a versioned telemetry protocol and WebSocket transport abstraction.
2. Build a ROS 2 gateway adapter.
3. Ingest real event-camera and LiDAR data.
4. Add SNN runtime instrumentation and inference traces.
5. Persist mission recordings and support experiment export/comparison.
6. Add richer robot maps, localisation and navigation overlays.

## Explicitly out of scope for the first release

- Autonomous navigation or robot decision-making inside NRC.
- Replacing ROS 2 or robot firmware.
- Certified safety control.
- Cloud fleet management, user billing or multi-tenant administration.

## Success criteria

- The same core dashboard and domain model run on Android and desktop.
- A judge can launch the simulator without hardware or API credentials.
- Sensor and perception views update continuously from the simulator.
- Emergency-stop state forces the UI command target to zero.
- A mission can be recorded and replayed in timestamp order.
- The shared safety and recording logic is covered by automated tests.
- The architecture clearly separates simulation, transport, domain and presentation.
