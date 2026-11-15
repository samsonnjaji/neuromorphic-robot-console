# NRC local development services

This Compose file starts PostgreSQL for the NRC Platform API. It binds the database to loopback only; it is intended for local development, not public deployment.

## 1. Start PostgreSQL

From the repository root:

```powershell
docker compose -f deploy/local/compose.yaml up -d postgres
docker compose -f deploy/local/compose.yaml ps
```

The local-only default password is `nrc_local_dev_only`. For a custom local password, copy `.env.example` to `.env`, set `NRC_DB_PASSWORD`, and use the same value in the API terminal. The root `.env` file is ignored by Git.

Do not reuse a development password outside a local workstation.

## 2. Run the API from PowerShell

Open a second terminal at the repository root:

```powershell
$env:NRC_DATABASE_URL = "jdbc:postgresql://localhost:5432/nrc"
$env:NRC_DATABASE_USER = "nrc"
$env:NRC_DATABASE_PASSWORD = "nrc_local_dev_only"
$env:NRC_API_HOST = "127.0.0.1"
$env:NRC_API_PORT = "8080"
gradle :platform-api:run
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
gradle :composeApp:run
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
gradle :composeApp:run
```

## 5. Build the Android app

The Android app currently uses the standalone simulator mode. From a separate terminal:

```powershell
gradle :androidApp:assembleDebug
```

Expected result: `BUILD SUCCESSFUL`. The debug APK is normally generated under `androidApp/build/outputs/apk/debug/`.

## 6. Stop PostgreSQL

```powershell
docker compose -f deploy/local/compose.yaml down
```

Use `docker compose -f deploy/local/compose.yaml down -v` only when you intentionally want to delete the local NRC database volume and all local experiment records.

## Current limitations

- This local service is not authenticated; it binds to localhost by default.
- PostgreSQL currently stores experiment metadata only.
- Live telemetry fan-out is in memory and is not durable.
- Raw MCAP/ROS bag/image/video recordings are not stored in PostgreSQL.
- The desktop API-relay mode uses synthetic telemetry to test the API path.
- Android is not yet connected to the API.
- Do not expose the API or database to a network until authentication, TLS, access controls and deployment hardening are implemented.
