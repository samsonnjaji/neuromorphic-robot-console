# Start NRC

NRC is Samson Sibona Njaji's long-term Kotlin Multiplatform robotics product, not only a KotlinConf submission.

Repository:
https://github.com/samsonnjaji/neuromorphic-robot-console

## 1. Get the source

```bash
git clone https://github.com/samsonnjaji/neuromorphic-robot-console.git
cd neuromorphic-robot-console
```

Open the project folder in Android Studio. Use JDK 17 and allow Gradle to sync. The project uses Kotlin 2.4.21, Compose Multiplatform 1.12.2, Android Gradle Plugin 9.3.1 and Gradle 9.7.0.

For Android builds, install Android SDK Platform 37.0 and Build Tools 37.0.0. The app currently compiles against API 37 while targeting API 36.

## 2. What currently works in the prototype

- Simulated 10 Hz robot telemetry.
- Event-camera activity and 2D LiDAR scan views.
- IMU, pose, detection, inference and SNN activity metrics.
- Simulated speed guard and emergency stop.
- Mission capture and deterministic in-memory replay.
- Versioned telemetry envelope and stream validation.
- Environment Robustness Lab with clear, rain, fog, low-light and dust/smoke profiles.
- Deterministic synthetic perturbations for LiDAR returns, event samples, detection confidence and inference latency.

The robustness profiles are illustrative software perturbations. They are not calibrated weather physics, measured model accuracy or safety certification.

No physical robot is connected and no hardware motion commands are transmitted.

## 3. Run later on your PC

The repository includes Gradle bootstrap scripts that download Gradle 9.7.0 and verify its SHA-256 checksum before use.

Windows PowerShell:

```powershell
.\scripts\gradle.ps1 :composeApp:run
.\scripts\gradle.ps1 :composeApp:jvmTest
.\scripts\gradle.ps1 :androidApp:assembleDebug
```

macOS / Linux:

```bash
./scripts/gradle.sh :composeApp:run
./scripts/gradle.sh :composeApp:jvmTest
./scripts/gradle.sh :androidApp:assembleDebug
```

## 4. Next engineering milestones

1. Validate the robustness workflow against real adverse-weather and event-vision recordings.
2. Add a versioned experiment manifest and reproducible report export.
3. Implement a read-only WebSocket telemetry adapter.
4. Build the ROS 2 gateway as a separate process.
5. Compare conventional and spiking models on identical labelled inputs.
6. Add hardware-specific latency, memory and power measurements.

## Safety

The current dashboard is simulator-only. Its UI safety envelope is not a certified safety system. Any physical robot must enforce independent speed limits, watchdogs, command validation and an emergency-stop circuit on the robot/controller side.
