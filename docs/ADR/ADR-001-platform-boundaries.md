# ADR-001: Multi-client platform with a shared robotics core

- **Status:** Accepted
- **Date:** 2026-11-15
- **Decision owner:** NRC project

## Context

NRC is intended to serve robotics researchers, university laboratories, startups and engineering teams through Android, desktop and web interfaces. Separate application implementations would duplicate experiment logic and eventually create inconsistent metrics and data models.

At the same time, ROS 2 middleware, machine-learning frameworks, cloud services and user interfaces have different runtime requirements. Forcing all of them into one binary or language would create unnecessary coupling.

## Decision

Adopt a modular platform with:

1. A Kotlin Multiplatform NRC Core for domain models, experiment definitions, validation, replay and common evaluation logic.
2. Kotlin/JVM Ktor Platform API for identity, device registry, experiment metadata, reports and live telemetry distribution.
3. Android and desktop Compose clients, initially using the existing Kotlin Multiplatform application foundation.
4. A separate browser client consuming the same versioned API.
5. A separate ROS 2 edge gateway for robot and recording connectivity.
6. Separate Python inference workers for conventional and spiking model execution.
7. PostgreSQL for metadata and S3-compatible object storage for large recordings and artifacts.

The API and experiment contracts are the integration boundaries. Clients do not connect directly to databases or model workers.

## Consequences

### Positive
- One canonical experiment model across clients.
- Existing Kotlin work remains useful.
- ROS 2 and ML frameworks can use their natural runtimes.
- Local, hybrid and cloud deployment remain possible.
- New interfaces do not require a second backend.

### Trade-offs
- Multiple services and languages require clear contracts and CI.
- The current Compose module must eventually be split to isolate pure core logic.
- Authentication, storage, deployment and observability add engineering work.

## Implementation sequence

1. Document and version telemetry/experiment contracts.
2. Extract the domain and experiment engine into an independent KMP core module.
3. Add a local Ktor API with health, experiment metadata and WebSocket telemetry.
4. Add local PostgreSQL and object-storage development services.
5. Connect the existing desktop client, then Android.
6. Add the read-only ROS 2 gateway and Python inference worker.
7. Build the web client against the same API.
8. Add cloud/hybrid deployment after local deployment is reproducible.

## Explicit non-goals

- Rewriting the existing applications immediately.
- Embedding ROS 2 in Android or shared Kotlin code.
- Reimplementing PyTorch or neuromorphic hardware SDKs in Kotlin.
- Enabling remote robot actuation in the initial release.
