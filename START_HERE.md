# Start NRC

NRC is Samson Sibona Njaji's individual KotlinConf project. The repository is now hosted at:

https://github.com/samsonnjaji/neuromorphic-robot-console

## 1. Get the source

Clone the repository:

```bash
git clone https://github.com/samsonnjaji/neuromorphic-robot-console.git
cd neuromorphic-robot-console
```

Open the project folder in Android Studio. Use JDK 17 and allow Gradle to sync. The project uses Kotlin 2.4.21, Compose Multiplatform 1.12.2, Android Gradle Plugin 9.3.1 and Gradle 9.7.0.

## 2. What currently works in the MVP

- Simulated 10 Hz robot telemetry.
- Event-camera activity view.
- 2D LiDAR scan view.
- IMU, pose, detection and inference metrics.
- Simulated SNN layer activity.
- Speed-limited simulated controls and emergency stop.
- Mission capture and deterministic replay in memory.

No physical robot is connected and no hardware motion commands are transmitted.

## 3. Run later on your PC

The repository includes Gradle bootstrap scripts that download Gradle 9.7.0 and verify its official SHA-256 checksum before use.

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

1. Verify Gradle sync, shared tests and Android/Desktop builds.
2. Add a versioned telemetry transport interface.
3. Implement a local WebSocket test gateway.
4. Build the ROS 2 adapter as a separate gateway process.
5. Connect real event-camera, LiDAR and IMU feeds after protocol and safety review.
6. Add persistent mission recordings and experiment export.

## Safety

The current dashboard is simulator-only. Its UI safety envelope is not a certified safety system. Any physical robot must enforce independent speed limits, watchdogs, command validation and an emergency-stop circuit on the robot/controller side.
