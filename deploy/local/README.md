# NRC local development services

This Compose file starts PostgreSQL for the NRC Platform API. It binds the database to loopback only; it is intended for local development, not public deployment.

## 1. Start PostgreSQL

From the repository root:

```powershell
docker compose -f deploy/local/compose.yaml up -d postgres
docker compose -f deploy/local/compose.yaml ps
```

If the Docker CLI is available only inside Ubuntu WSL, run the Compose commands in that WSL distribution from the Windows checkout mounted under `/mnt/c/Users/njaji/Projects/neuromorphic-robot-console`. The API and desktop can still run from Windows PowerShell and connect through `localhost`.

By default PostgreSQL is exposed on host port `5432`. If another local PostgreSQL service already owns that port, set `NRC_POSTGRES_HOST_PORT=5435` in the repository-root `.env` file before starting Compose. The container still listens on `5432`; only the host-side port changes. In that case, use `jdbc:postgresql://localhost:5435/nrc` for `NRC_DATABASE_URL` below. The default remains `5432` for machines without a port conflict.

The local-only default password is `nrc_local_dev_only`. For a custom local password, copy `.env.example` to `.env`, set `NRC_DB_PASSWORD`, and use the same value in the API terminal. The root `.env` file is ignored by Git.

Do not reuse a development password outside a local workstation.

## 2. Run the API from PowerShell

Open a second terminal at the repository root:

```powershell
$env:NRC_DATABASE_URL = "jdbc:postgresql://localhost:5432/nrc"
# If NRC_POSTGRES_HOST_PORT=5435 is set in .env, use this instead:
# $env:NRC_DATABASE_URL = "jdbc:postgresql://localhost:5435/nrc"
$env:NRC_DATABASE_USER = "nrc"
$env:NRC_DATABASE_PASSWORD = "nrc_local_dev_only"
$env:NRC_API_HOST = "127.0.0.1"
$env:NRC_API_PORT = "8080"
.\gradlew.bat :platform-api:run
```

If you set a custom `NRC_DB_PASSWORD` in `.env`, replace the API password above with that same value.

Flyway applies the versioned SQL migrations at startup. When `NRC_DATABASE_URL` is absent, the API falls back to an in-memory repository and prints a warning; those records are not durable.

## 3. Verify the API

In another PowerShell terminal:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/v1/health
Invoke-RestMethod http://127.0.0.1:8080/api/v1/experiments
```

Expected health response includes `status = ok`, `service = nrc-platform-api`, and `apiVersion = v1`.

## 4. Run the desktop API-relay test

Keep the API running. Open a third terminal at the repository root:

```powershell
$env:NRC_API_BASE_URL = "http://127.0.0.1:8080"
$env:NRC_ROBOT_ID = "nrc-desktop-relay"
.\gradlew.bat :composeApp:run
```

The desktop client will:
1. Generate clearly labelled synthetic simulator telemetry.
2. POST each snapshot to the local API.
3. Subscribe to the robot's WebSocket stream.
4. Render the snapshots received back from the API.

The dashboard should show `API RELAY · SYNTHETIC ONLINE` and live event-camera, LiDAR, IMU and SNN panels. This verifies the desktop-to-API-to-desktop data path; it is not a real robot connection and does not enable actuation.

To run the original standalone simulator UI instead, remove the API environment variables before launching:

```powershell
Remove-Item Env:NRC_API_BASE_URL -ErrorAction SilentlyContinue
Remove-Item Env:NRC_ROBOT_ID -ErrorAction SilentlyContinue
.\gradlew.bat :composeApp:run
```

## 5. Verify experiment runs and recording sessions

Keep the API connected to PostgreSQL. In a new PowerShell terminal:

```powershell
$BaseUrl = "http://127.0.0.1:8080"

$Experiment = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/experiments" -ContentType "application/json" -Body (@{
    name = "NRC lifecycle smoke test"
    description = "Local API and PostgreSQL verification"
    scenario = "HEAVY_RAIN"
    sourceType = "SIMULATOR"
    sourceId = "nrc-local-simulator"
    modelIdentifier = "nrc-smoke-model"
    modelVersion = "0.1"
} | ConvertTo-Json)

$Run = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/experiments/$($Experiment.id)/runs" -ContentType "application/json" -Body (@{
    sourceRevision = "local-smoke"
    configuration = @{ dataOrigin = "SYNTHETIC"; scenario = "HEAVY_RAIN" }
} | ConvertTo-Json -Depth 8)

Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/runs/$($Run.id)/transition" -ContentType "application/json" -Body (@{ status = "RUNNING" } | ConvertTo-Json)

$Recording = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/runs/$($Run.id)/recordings" -ContentType "application/json" -Body (@{
    format = "MCAP"
    sourceId = "nrc-local-simulator"
    metadata = @{ dataOrigin = "SYNTHETIC"; topicCount = 4 }
} | ConvertTo-Json -Depth 8)

Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/recordings/$($Recording.id)/transition" -ContentType "application/json" -Body (@{ status = "RECORDING" } | ConvertTo-Json)

Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/recordings/$($Recording.id)/transition" -ContentType "application/json" -Body (@{
    status = "FINALIZED"
    sampleCount = 120
    metadata = @{ dataOrigin = "SYNTHETIC"; topicCount = 4 }
} | ConvertTo-Json -Depth 8)

Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/runs/$($Run.id)/transition" -ContentType "application/json" -Body (@{
    status = "COMPLETED"
    resultSummary = @{ frames = 120; dataOrigin = "SYNTHETIC" }
} | ConvertTo-Json -Depth 8)

Invoke-RestMethod "$BaseUrl/api/v1/runs/$($Run.id)"
Invoke-RestMethod "$BaseUrl/api/v1/runs/$($Run.id)/recordings"
```

Expected: the run ends as `COMPLETED`, the recording session ends as `FINALIZED`, and both records are returned from PostgreSQL after an API restart.

This verifies lifecycle metadata only. It does not upload an MCAP file; MinIO/S3 artifact upload is the next milestone.

## 6. Build the Android app

The Android app currently uses the standalone simulator mode. From a separate terminal:

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

Expected result: `BUILD SUCCESSFUL`. The debug APK is normally generated under `androidApp/build/outputs/apk/debug/`.

## 7. Stop PostgreSQL

```powershell
docker compose -f deploy/local/compose.yaml down
```

Use `docker compose -f deploy/local/compose.yaml down -v` only when you intentionally want to delete the local NRC database volume and all local experiment records.

## Current limitations

- This local service is not authenticated; it binds to localhost by default.
- PostgreSQL stores experiment definitions, run metadata and recording-session metadata; raw MCAP/ROS bag/image/video files are not stored in PostgreSQL.
- Live telemetry fan-out is in memory and is not durable.
- Raw MCAP/ROS bag/image/video recordings are not uploaded yet; S3-compatible object storage is the next milestone.
- The desktop API-relay mode uses synthetic telemetry to test the API path.
- Android is not yet connected to the API.
- Do not expose the API or database to a network until authentication, TLS, access controls and deployment hardening are implemented.
