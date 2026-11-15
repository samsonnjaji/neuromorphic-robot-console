# NRC — Neuromorphic Robotics Console

**An observability, experiment-replay and safe-operation console for autonomous and neuromorphic robots.**

NRC is an original Kotlin Multiplatform application led and maintained by **Samson Sibona Njaji**. It brings robot telemetry, event-camera activity, LiDAR scans, inertial measurements, AI perception outputs, safety state and mission replay into one research-oriented interface.

> **Current status:** MVP implementation in progress. The deterministic simulator, dashboard, safety-limited simulated controls, SNN activity visualisation and in-memory mission recording/replay are implemented. The full Gradle/Android build has not yet been verified in this environment. NRC does not connect to or control physical hardware yet.

## What NRC does

A robotics researcher can use NRC to:

- Monitor robot connectivity, battery, velocity, pose and compute load.
- Visualise sparse event-camera activity and 2D LiDAR range scans.
- Inspect perception detections, confidence, bearing and inference latency.
- Observe simulated spiking-network layer activity, firing rates and mean membrane potential.
- Observe IMU readings and robot operating state.
- Exercise simulated speed commands, pause/resume and emergency-stop behaviour.
- Record a telemetry session and replay it deterministically for experiment review.

## Why it is different

NRC is designed around **neuromorphic and event-driven robotics**, not just generic robot status cards. The application makes the perception-to-action pipeline observable: sensor events enter the system, perception produces detections, safety logic constrains commands, and the operator can inspect the resulting behaviour. Its initial demo runs without robot hardware or cloud credentials.

## Platforms and technology

- Kotlin Multiplatform and Compose Multiplatform.
- Shared application UI and domain logic.
- Android application (`androidApp`).
- JVM desktop application (`composeApp`).
- Kotlin Coroutines and StateFlow for telemetry state.
- Hardware-independent telemetry models and bounded mission recording.
- A planned ROS 2 gateway using a versioned WebSocket protocol.

## Repository structure

```text
androidApp/                 Android application entry point
composeApp/
  src/commonMain/            Shared UI, domain models, simulator and mission recorder
  src/commonTest/            Shared safety and mission-recorder tests
  src/jvmMain/               Desktop application entry point
docs/
  PRODUCT_SPEC.md            Product definition and MVP scope
  ARCHITECTURE.md            System architecture and data flow
  SAFETY_MODEL.md            Safety boundaries and hardware integration principles
  TELEMETRY_PROTOCOL.md      Versioned telemetry contract
scripts/
  gradle.ps1                 Verified Gradle bootstrap for Windows
  gradle.sh                  Verified Gradle bootstrap for macOS/Linux
```

## Getting started

Requirements:

- JDK 17 or newer.
- Android Studio with Kotlin Multiplatform support and Android SDK Platform 36 for Android builds.
- Internet access on first build to download Gradle and dependencies.

The repository includes a small Gradle bootstrap script. It downloads Gradle 9.7.0 and verifies the official SHA-256 checksum before running it.

### Windows PowerShell

```powershell
.\scripts\gradle.ps1 :composeApp:run
.\scripts\gradle.ps1 :composeApp:jvmTest
.\scripts\gradle.ps1 :androidApp:assembleDebug
```

### macOS / Linux

```bash
./scripts/gradle.sh :composeApp:run
./scripts/gradle.sh :composeApp:jvmTest
./scripts/gradle.sh :androidApp:assembleDebug
```

The first command downloads Gradle 9.7.0 into `.tools/`. Subsequent runs reuse that verified local distribution.

## Current MVP boundary

The dashboard uses simulated telemetry. It is intentionally hardware-independent so the interface, domain model, safety behaviours and mission replay can be demonstrated without a robot. The simulator generates event-camera samples, 72-point LiDAR scans, IMU readings, pose, object detections, SNN layer activity, event rate and inference latency.

Mission recording is currently in-memory and bounded to 3,600 frames. Persistence, ROS 2 transport, real event-camera/LiDAR ingestion and SNN runtime instrumentation are subsequent milestones.

## Safety statement

The UI-level safety envelope is a demonstration guard, not a certified safety system. A physical robot must enforce speed limits, watchdogs, emergency-stop handling and command validation independently on the robot/controller side. NRC must never be the only safety layer. The current application sends no physical robot commands.

## Licence

MIT. See [LICENSE](LICENSE).
