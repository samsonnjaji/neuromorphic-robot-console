# NRC local development services

This Compose file starts PostgreSQL for the NRC Platform API. It binds the database to loopback only; it is intended for local development, not public deployment.

## Start PostgreSQL

From the repository root:

```powershell
docker compose -f deploy/local/compose.yaml up -d postgres
docker compose -f deploy/local/compose.yaml ps
```

The local-only default password is `nrc_local_dev_only`. Override it by setting `NRC_DB_PASSWORD` before starting Compose. Do not reuse this development password outside a local workstation.

## Run the API from PowerShell

Open a second terminal at the repository root:

```powershell
$env:NRC_DATABASE_URL = "jdbc:postgresql://localhost:5432/nrc"
$env:NRC_DATABASE_USER = "nrc"
$env:NRC_DATABASE_PASSWORD = "nrc_local_dev_only"
$env:NRC_API_HOST = "127.0.0.1"
$env:NRC_API_PORT = "8080"
gradle :platform-api:run
```

Flyway applies the versioned SQL migrations at startup. When `NRC_DATABASE_URL` is absent, the API falls back to an in-memory repository and prints a warning; those records are not durable.

## Verify the API

In another PowerShell terminal:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/v1/health
Invoke-RestMethod http://127.0.0.1:8080/api/v1/experiments
```

Expected health response includes `status = ok`, `service = nrc-platform-api`, and `apiVersion = v1`.

## Stop PostgreSQL

```powershell
docker compose -f deploy/local/compose.yaml down
```

Use `docker compose -f deploy/local/compose.yaml down -v` only when you intentionally want to delete the local NRC database volume and all local experiment records.

## Current limitations

- This local service is not authenticated; it binds to localhost by default.
- PostgreSQL currently stores experiment metadata only.
- Live telemetry fan-out is in memory and is not durable.
- Raw MCAP/ROS bag/image/video recordings are not stored in PostgreSQL.
- Do not expose the API or database to a network until authentication, TLS, access controls and deployment hardening are implemented.
