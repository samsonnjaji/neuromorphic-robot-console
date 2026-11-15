# NRC local development services

This Compose file starts PostgreSQL and RustFS for the NRC Platform API. Both bind to loopback only and are intended for local development, not public deployment.

## 1. Configure local services

From the repository root:

```powershell
Copy-Item .env.example .env
```

Edit `.env` and populate the blank credential fields before starting Compose. If you already have an untracked `.env` from the earlier local setup, rename its PostgreSQL Compose credential key to `NRC_DB_CREDENTIAL` while retaining the existing value; this does not change the PostgreSQL role password.
- Set `NRC_POSTGRES_HOST_PORT=5435` if native PostgreSQL already owns host port 5432.
- Set `NRC_DB_CREDENTIAL` to the existing local password for the `nrc` database role if reusing the current PostgreSQL data volume. Changing this environment variable alone does not change an existing database user's password.
- Set a strong, unique local `NRC_OBJECT_STORE_SECRET_KEY` and a local access key. Use the same values in the API process environment. The example file intentionally contains no usable credentials.

The root `.env` file is ignored by Git. Never commit credentials.

Default local ports:
- PostgreSQL: `127.0.0.1:5432` (or the configured host port, e.g. 5435)
- RustFS S3 API: `127.0.0.1:9000`
- RustFS console: `http://127.0.0.1:9001`

If the Docker CLI is available only inside Ubuntu WSL, run Compose from that WSL distribution after changing to the Windows checkout mounted at `/mnt/c/Users/njaji/Projects/neuromorphic-robot-console`:

```bash
docker compose -f deploy/local/compose.yaml up -d postgres rustfs
docker compose -f deploy/local/compose.yaml ps
```

The Compose file includes a one-shot volume-permission helper for RustFS because its container runs as a non-root user. Existing named volumes are preserved; do not use `down -v`.

## 2. Start PostgreSQL and RustFS

From PowerShell when the Docker CLI is available:

```powershell
docker compose -f deploy/local/compose.yaml up -d postgres rustfs
docker compose -f deploy/local/compose.yaml ps
```

Wait until both services report healthy. The RustFS console is available at `http://127.0.0.1:9001`; use the access key and secret from `.env`.

If an existing PostgreSQL container is recreated because the Compose configuration changed, the named data volume is retained. Never delete it during routine updates.

## 3. Run the API from PowerShell

Open a second terminal at the repository root. Set the database port to match `NRC_POSTGRES_HOST_PORT` in `.env`. For a workstation where native PostgreSQL owns 5432, use 5435:

```powershell
$env:NRC_DATABASE_URL = "jdbc:postgresql://localhost:5435/nrc"
$env:NRC_DATABASE_USER = "nrc"
$LocalEnv = @{}
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)=(.*)$') {
        $LocalEnv[$matches[1].Trim()] = $matches[2].Trim()
    }
}
$env:NRC_DATABASE_PASSWORD = $LocalEnv["NRC_DB_CREDENTIAL"]
$env:NRC_OBJECT_STORE_ENDPOINT = $LocalEnv["NRC_OBJECT_STORE_ENDPOINT"]
$env:NRC_OBJECT_STORE_ACCESS_KEY = $LocalEnv["NRC_OBJECT_STORE_ACCESS_KEY"]
$env:NRC_OBJECT_STORE_SECRET_KEY = $LocalEnv["NRC_OBJECT_STORE_SECRET_KEY"]
$env:NRC_OBJECT_STORE_BUCKET = $LocalEnv["NRC_OBJECT_STORE_BUCKET"]
$env:NRC_OBJECT_STORE_REGION = $LocalEnv["NRC_OBJECT_STORE_REGION"]
$env:NRC_OBJECT_STORE_PATH_STYLE = $LocalEnv["NRC_OBJECT_STORE_PATH_STYLE"]
$env:NRC_OBJECT_STORE_PRESIGN_TTL_SECONDS = $LocalEnv["NRC_OBJECT_STORE_PRESIGN_TTL_SECONDS"]
$env:NRC_ARTIFACT_PENDING_UPLOAD_TTL_SECONDS = $LocalEnv["NRC_ARTIFACT_PENDING_UPLOAD_TTL_SECONDS"]
$env:NRC_ARTIFACT_CLEANUP_GRACE_SECONDS = $LocalEnv["NRC_ARTIFACT_CLEANUP_GRACE_SECONDS"]
$env:NRC_ARTIFACT_CLEANUP_INTERVAL_SECONDS = $LocalEnv["NRC_ARTIFACT_CLEANUP_INTERVAL_SECONDS"]
$env:NRC_ARTIFACT_CLEANUP_BATCH_SIZE = $LocalEnv["NRC_ARTIFACT_CLEANUP_BATCH_SIZE"]
$env:NRC_API_HOST = "127.0.0.1"
$env:NRC_API_PORT = "8080"

.\gradlew.bat :platform-api:run
```

For machines using the default PostgreSQL port, change the database URL to `jdbc:postgresql://localhost:5432/nrc`. The API process does not automatically import `.env`; set the environment variables explicitly. Flyway applies versioned migrations at startup. When `NRC_DATABASE_URL` is absent, the API uses in-memory repositories and records are not durable. When `NRC_OBJECT_STORE_ENDPOINT` is absent, artifact upload/download endpoints return HTTP 503.

The API creates the configured bucket at startup if it does not already exist. RustFS is the local development provider; the application uses the provider-neutral AWS SDK for Java v2 S3 API. When PostgreSQL and object storage are both configured, the API also starts a background artifact-maintenance worker.

The default cleanup policy gives a pending upload a 24-hour inactivity window, then waits a further 1-hour grace period after the latest tracked presigned upload URL expires. The worker scans every 15 minutes in batches of 100, aborts abandoned multipart sessions, deletes abandoned/failed objects and retries cleanup after storage errors. These values are configurable with the `NRC_ARTIFACT_*` environment variables. Verified artifacts are never removed by this worker; verified-object retention policies are a separate future feature.

## 4. Verify the API

In another PowerShell terminal:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/v1/health
Invoke-RestMethod http://127.0.0.1:8080/api/v1/experiments
```

Expected health response includes `status = ok`, `service = nrc-platform-api` and `apiVersion = v1`.

## 5. Run the desktop API-relay test

Keep the API running. Open another terminal at the repository root:

```powershell
$env:NRC_API_BASE_URL = "http://127.0.0.1:8080"
$env:NRC_ROBOT_ID = "nrc-desktop-relay"
.\gradlew.bat :composeApp:run
```

The desktop client generates clearly labelled synthetic simulator telemetry, POSTs each snapshot to the API, subscribes to the robot's WebSocket stream and renders the snapshots received back. The dashboard should show `API RELAY · SYNTHETIC ONLINE` and live event-camera, LiDAR, IMU and SNN panels. This is not a physical robot connection and does not enable actuation.

## 6. Verify experiment, recording and artifact persistence

First create and finalize an experiment recording using the API lifecycle endpoints. Artifacts can only be attached to a `FINALIZED` recording session. Run this script in PowerShell and keep the same session open so `$RecordingId` remains available:

```powershell
$BaseUrl = "http://127.0.0.1:8080"
$Experiment = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/experiments" -ContentType "application/json" -Body (@{
    name = "NRC artifact smoke test"
    description = "Local PostgreSQL and RustFS verification"
    scenario = "HEAVY_RAIN"
    sourceType = "SIMULATOR"
    sourceId = "nrc-local-simulator"
    modelIdentifier = "nrc-smoke-model"
    modelVersion = "0.1"
} | ConvertTo-Json)
$Run = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/experiments/$($Experiment.id)/runs" -ContentType "application/json" -Body (@{ sourceRevision = "local-smoke" } | ConvertTo-Json)
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/runs/$($Run.id)/transition" -ContentType "application/json" -Body (@{ status = "RUNNING" } | ConvertTo-Json)
$Recording = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/runs/$($Run.id)/recordings" -ContentType "application/json" -Body (@{ format = "MCAP"; sourceId = "nrc-local-simulator"; metadata = @{ dataOrigin = "SYNTHETIC" } } | ConvertTo-Json -Depth 8)
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/recordings/$($Recording.id)/transition" -ContentType "application/json" -Body (@{ status = "RECORDING" } | ConvertTo-Json)
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/recordings/$($Recording.id)/transition" -ContentType "application/json" -Body (@{ status = "FINALIZED"; sampleCount = 120 } | ConvertTo-Json)
$RecordingId = $Recording.id
```

For a small artifact, calculate its SHA-256 and size in PowerShell:

```powershell
$SamplePath = Join-Path $env:TEMP "nrc-artifact-smoke.txt"
[System.IO.File]::WriteAllText($SamplePath, "NRC artifact storage smoke test")
$SampleSize = (Get-Item $SamplePath).Length
$SampleHash = (Get-FileHash -Algorithm SHA256 $SamplePath).Hash.ToLowerInvariant()
```

Create an artifact upload intent, replacing `$RecordingId` with the finalized recording session UUID:

```powershell
$BaseUrl = "http://127.0.0.1:8080"
$Intent = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/recordings/$RecordingId/artifacts" -ContentType "application/json" -Body (@{
    fileName = [System.IO.Path]::GetFileName($SamplePath)
    contentType = "text/plain"
    sizeBytes = $SampleSize
    sha256Hex = $SampleHash
} | ConvertTo-Json)

Invoke-WebRequest -Method Put -Uri $Intent.uploadUrl -InFile $SamplePath -ContentType "text/plain" -UseBasicParsing

$Verified = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/artifacts/$($Intent.artifact.id)/complete" -ContentType "application/json" -Body "{}"
$Verified

$Download = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/artifacts/$($Intent.artifact.id)/download-url"
$DownloadedPath = Join-Path $env:TEMP "nrc-artifact-downloaded.txt"
Invoke-WebRequest -Uri $Download.downloadUrl -OutFile $DownloadedPath -UseBasicParsing
(Get-FileHash -Algorithm SHA256 $DownloadedPath).Hash.ToLowerInvariant() -eq $SampleHash
```

Expected: artifact status is `VERIFIED`, a download URL is returned, and the final hash comparison is `True`.

Files up to 64 MiB use a presigned single PUT. Larger files use multipart upload: request a URL for each part, upload the part, retain its ETag, and submit the complete ordered part list to `POST /api/v1/artifacts/{artifactId}/complete`.

The API verifies the actual object byte count and SHA-256 by streaming it from object storage. A failed integrity check marks the artifact `FAILED` and attempts to delete the uploaded object. A download URL is issued only for verified artifacts.

## 7. Verify abandoned-upload cleanup (quick local test)

For a quick test only, stop the API and restart it with these temporary PowerShell settings before creating the pending artifact:

```powershell
$env:NRC_OBJECT_STORE_PRESIGN_TTL_SECONDS = "60"
$env:NRC_ARTIFACT_PENDING_UPLOAD_TTL_SECONDS = "60"
$env:NRC_ARTIFACT_CLEANUP_GRACE_SECONDS = "0"
$env:NRC_ARTIFACT_CLEANUP_INTERVAL_SECONDS = "1"
```

Use the already-finalized $RecordingId from section 6. Create an upload intent but do not upload the file:

```powershell
$PendingPath = Join-Path $env:TEMP "nrc-pending-cleanup.txt"
[System.IO.File]::WriteAllText($PendingPath, "NRC cleanup test")
$PendingSize = (Get-Item $PendingPath).Length
$PendingHash = (Get-FileHash -Algorithm SHA256 $PendingPath).Hash.ToLowerInvariant()
$PendingIntent = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/recordings/$RecordingId/artifacts" -ContentType "application/json" -Body (@{
    fileName = "nrc-pending-cleanup.txt"
    contentType = "text/plain"
    sizeBytes = $PendingSize
    sha256Hex = $PendingHash
} | ConvertTo-Json)
$PendingArtifactId = $PendingIntent.artifact.id
Start-Sleep -Seconds 65
$ExpiredArtifact = Invoke-RestMethod -Uri "$BaseUrl/api/v1/artifacts/$PendingArtifactId"
$ExpiredArtifact.status
```

Expected status: ABORTED. The worker should log a cleanup summary. Restart the API with the normal values from .env after this test. Do not use the short test TTL in a shared or production environment.

## 8. Build the Android app

The Android app currently uses standalone simulator mode:

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

Expected result: BUILD SUCCESSFUL. The debug APK is normally generated under androidApp/build/outputs/apk/debug/.

## 9. Stop local services

```powershell
docker compose -f deploy/local/compose.yaml down
```

This stops services but preserves named volumes. Do not use down -v unless you intentionally want to delete the local PostgreSQL database and all RustFS objects.

## Current limitations

- This local service is not authenticated; PostgreSQL and RustFS bind to localhost by default.
- PostgreSQL stores experiment definitions, run/recording metadata and artifact registry records. Raw MCAP/ROS bag/image/video files are stored in S3-compatible object storage.
- Live telemetry fan-out is in memory and is not durable.
- Expired pending/failed uploads are automatically aborted/deleted, with retry after storage errors. Retention policies for verified artifacts are not implemented yet.
- The desktop API-relay mode uses synthetic telemetry to test the API path.
- Android is not yet connected to the API.
- Do not expose the API, database or object store to a network until authentication, TLS, access controls and deployment hardening are implemented.
) {
        $LocalEnv[$matches[1].Trim()] = $matches[2].Trim()
    }
}
$env:NRC_DATABASE_PASSWORD = $LocalEnv["NRC_DB_CREDENTIAL"]
$env:NRC_OBJECT_STORE_ENDPOINT = $LocalEnv["NRC_OBJECT_STORE_ENDPOINT"]
$env:NRC_OBJECT_STORE_ACCESS_KEY = $LocalEnv["NRC_OBJECT_STORE_ACCESS_KEY"]
$env:NRC_OBJECT_STORE_SECRET_KEY = $LocalEnv["NRC_OBJECT_STORE_SECRET_KEY"]
$env:NRC_OBJECT_STORE_BUCKET = $LocalEnv["NRC_OBJECT_STORE_BUCKET"]
$env:NRC_OBJECT_STORE_REGION = $LocalEnv["NRC_OBJECT_STORE_REGION"]
$env:NRC_OBJECT_STORE_PATH_STYLE = $LocalEnv["NRC_OBJECT_STORE_PATH_STYLE"]
$env:NRC_OBJECT_STORE_PRESIGN_TTL_SECONDS = $LocalEnv["NRC_OBJECT_STORE_PRESIGN_TTL_SECONDS"]
$env:NRC_ARTIFACT_PENDING_UPLOAD_TTL_SECONDS = $LocalEnv["NRC_ARTIFACT_PENDING_UPLOAD_TTL_SECONDS"]
$env:NRC_ARTIFACT_CLEANUP_GRACE_SECONDS = $LocalEnv["NRC_ARTIFACT_CLEANUP_GRACE_SECONDS"]
$env:NRC_ARTIFACT_CLEANUP_INTERVAL_SECONDS = $LocalEnv["NRC_ARTIFACT_CLEANUP_INTERVAL_SECONDS"]
$env:NRC_ARTIFACT_CLEANUP_BATCH_SIZE = $LocalEnv["NRC_ARTIFACT_CLEANUP_BATCH_SIZE"]
$env:NRC_ARTIFACT_PENDING_UPLOAD_TTL_SECONDS = "86400"
$env:NRC_ARTIFACT_CLEANUP_GRACE_SECONDS = "3600"
$env:NRC_ARTIFACT_CLEANUP_INTERVAL_SECONDS = "900"
$env:NRC_ARTIFACT_CLEANUP_BATCH_SIZE = "100"
$env:NRC_API_HOST = "127.0.0.1"
$env:NRC_API_PORT = "8080"


.\gradlew.bat :platform-api:run
```

For machines using the default PostgreSQL port, change the database URL to `jdbc:postgresql://localhost:5432/nrc`. The API process does not automatically import `.env`; set the environment variables explicitly. Flyway applies versioned migrations at startup. When `NRC_DATABASE_URL` is absent, the API uses in-memory repositories and records are not durable. When `NRC_OBJECT_STORE_ENDPOINT` is absent, artifact upload/download endpoints return HTTP 503.

The API creates the configured bucket at startup if it does not already exist. RustFS is the local development provider; the application uses the provider-neutral AWS SDK for Java v2 S3 API. When PostgreSQL and object storage are both configured, the API also starts a background artifact-maintenance worker.

The default cleanup policy gives a pending upload a 24-hour inactivity window, then waits a further 1-hour grace period after the latest tracked presigned upload URL expires. The worker scans every 15 minutes in batches of 100, aborts abandoned multipart sessions, deletes abandoned/failed objects and retries cleanup after storage errors. These values are configurable with the `NRC_ARTIFACT_*` environment variables. Verified artifacts are never removed by this worker; verified-object retention policies are a separate future feature.

## 4. Verify the API

In another PowerShell terminal:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/v1/health
Invoke-RestMethod http://127.0.0.1:8080/api/v1/experiments
```

Expected health response includes `status = ok`, `service = nrc-platform-api` and `apiVersion = v1`.

## 5. Run the desktop API-relay test

Keep the API running. Open another terminal at the repository root:

```powershell
$env:NRC_API_BASE_URL = "http://127.0.0.1:8080"
$env:NRC_ROBOT_ID = "nrc-desktop-relay"
.\gradlew.bat :composeApp:run
```

The desktop client generates clearly labelled synthetic simulator telemetry, POSTs each snapshot to the API, subscribes to the robot's WebSocket stream and renders the snapshots received back. The dashboard should show `API RELAY · SYNTHETIC ONLINE` and live event-camera, LiDAR, IMU and SNN panels. This is not a physical robot connection and does not enable actuation.

## 6. Verify experiment, recording and artifact persistence

First create and finalize an experiment recording using the API lifecycle endpoints. Artifacts can only be attached to a `FINALIZED` recording session. Run this script in PowerShell and keep the same session open so `$RecordingId` remains available:

```powershell
$BaseUrl = "http://127.0.0.1:8080"
$Experiment = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/experiments" -ContentType "application/json" -Body (@{
    name = "NRC artifact smoke test"
    description = "Local PostgreSQL and RustFS verification"
    scenario = "HEAVY_RAIN"
    sourceType = "SIMULATOR"
    sourceId = "nrc-local-simulator"
    modelIdentifier = "nrc-smoke-model"
    modelVersion = "0.1"
} | ConvertTo-Json)
$Run = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/experiments/$($Experiment.id)/runs" -ContentType "application/json" -Body (@{ sourceRevision = "local-smoke" } | ConvertTo-Json)
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/runs/$($Run.id)/transition" -ContentType "application/json" -Body (@{ status = "RUNNING" } | ConvertTo-Json)
$Recording = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/runs/$($Run.id)/recordings" -ContentType "application/json" -Body (@{ format = "MCAP"; sourceId = "nrc-local-simulator"; metadata = @{ dataOrigin = "SYNTHETIC" } } | ConvertTo-Json -Depth 8)
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/recordings/$($Recording.id)/transition" -ContentType "application/json" -Body (@{ status = "RECORDING" } | ConvertTo-Json)
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/recordings/$($Recording.id)/transition" -ContentType "application/json" -Body (@{ status = "FINALIZED"; sampleCount = 120 } | ConvertTo-Json)
$RecordingId = $Recording.id
```

For a small artifact, calculate its SHA-256 and size in PowerShell:

```powershell
$SamplePath = Join-Path $env:TEMP "nrc-artifact-smoke.txt"
[System.IO.File]::WriteAllText($SamplePath, "NRC artifact storage smoke test")
$SampleSize = (Get-Item $SamplePath).Length
$SampleHash = (Get-FileHash -Algorithm SHA256 $SamplePath).Hash.ToLowerInvariant()
```

Create an artifact upload intent, replacing `$RecordingId` with the finalized recording session UUID:

```powershell
$BaseUrl = "http://127.0.0.1:8080"
$Intent = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/recordings/$RecordingId/artifacts" -ContentType "application/json" -Body (@{
    fileName = [System.IO.Path]::GetFileName($SamplePath)
    contentType = "text/plain"
    sizeBytes = $SampleSize
    sha256Hex = $SampleHash
} | ConvertTo-Json)

Invoke-WebRequest -Method Put -Uri $Intent.uploadUrl -InFile $SamplePath -ContentType "text/plain" -UseBasicParsing

$Verified = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/artifacts/$($Intent.artifact.id)/complete" -ContentType "application/json" -Body "{}"
$Verified

$Download = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/artifacts/$($Intent.artifact.id)/download-url"
$DownloadedPath = Join-Path $env:TEMP "nrc-artifact-downloaded.txt"
Invoke-WebRequest -Uri $Download.downloadUrl -OutFile $DownloadedPath -UseBasicParsing
(Get-FileHash -Algorithm SHA256 $DownloadedPath).Hash.ToLowerInvariant() -eq $SampleHash
```

Expected: artifact status is `VERIFIED`, a download URL is returned, and the final hash comparison is `True`.

Files up to 64 MiB use a presigned single PUT. Larger files use multipart upload: request a URL for each part, upload the part, retain its ETag, and submit the complete ordered part list to `POST /api/v1/artifacts/{artifactId}/complete`.

The API verifies the actual object byte count and SHA-256 by streaming it from object storage. A failed integrity check marks the artifact `FAILED` and attempts to delete the uploaded object. A download URL is issued only for verified artifacts.

## 7. Build the Android app

The Android app currently uses standalone simulator mode:

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

Expected result: `BUILD SUCCESSFUL`. The debug APK is normally generated under `androidApp/build/outputs/apk/debug/`.

## 8. Stop local services

```powershell
docker compose -f deploy/local/compose.yaml down
```

This stops services but preserves named volumes. Do not use `down -v` unless you intentionally want to delete the local PostgreSQL database and all RustFS objects.

## Current limitations

- This local service is not authenticated; PostgreSQL and RustFS bind to localhost by default.
- PostgreSQL stores experiment definitions, run/recording metadata and artifact registry records. Raw MCAP/ROS bag/image/video files are stored in S3-compatible object storage.
- Live telemetry fan-out is in memory and is not durable.
- Expired pending/failed uploads are automatically aborted/deleted, with retry after storage errors. Retention policies for verified artifacts are not implemented yet.
- The desktop API-relay mode uses synthetic telemetry to test the API path.
- Android is not yet connected to the API.
- Do not expose the API, database or object store to a network until authentication, TLS, access controls and deployment hardening are implemented.
