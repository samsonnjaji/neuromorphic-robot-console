# NRC Edge Gateway

The edge gateway currently provides offline MCAP recording ingestion in `services/edge-gateway`.

It validates MCAP files, indexes channel/topic/schema metadata, records message and sequence counts, preserves publisher and recorder timestamps separately in nanoseconds, computes SHA-256, and uploads the original recording plus a versioned provenance manifest through the existing NRC Platform API and artifact store. The manifest separates `dataOrigin` (`PHYSICAL_SENSOR`, `SIMULATED`, `DATASET` or `UNKNOWN`) from `ingestionMode` (`RECORDED`).

This is deliberately not described as a live ROS 2 gateway yet. It does not subscribe to ROS 2 topics, decode CDR payloads, map sensor messages into the dashboard's `RobotTelemetry` model, or control a robot. Payload bytes remain unchanged in the MCAP artifact. Frame IDs or units inside serialized message bodies are not decoded in this first increment.

See the [edge-gateway usage and implementation guide](../services/edge-gateway/README.md) and the [versioned recording manifest schema](../schemas/edge-recording-manifest.v1.schema.json).

## Data flow

1. Inspect the MCAP header, footer, channels and messages.
2. Compute the original file size and SHA-256 using bounded reads.
3. Generate an `edge-recording-manifest.v1` with explicit timestamp units and source provenance.
4. Create an NRC experiment run and MCAP recording session through the existing API.
5. Finalize the recording session and upload the original MCAP and JSON manifest as separate artifacts.
6. Let the Platform API verify object size and SHA-256 before marking artifacts `VERIFIED`.
7. Mark the experiment run `COMPLETED` only after both artifacts are verified.

## Provenance policy

The gateway never infers that an MCAP file came from a physical sensor. `dataOrigin` is supplied by the operator and defaults to `UNKNOWN`; `ingestionMode` is fixed to `RECORDED` because the input is an MCAP recording. A generated demo fixture must be ingested as `SIMULATED`.

## Timestamp policy

MCAP `publish_time` and `log_time` are separate nanosecond values. A zero publish timestamp is counted as unavailable; it is never silently replaced with the recorder timestamp. The manifest-generation time is separately labelled in Unix milliseconds.

## Synthetic smoke test

Generate a labelled test recording with `python services/edge-gateway/examples/create_synthetic_mcap.py --output ./synthetic-demo.mcap --messages 10`, inspect it with `nrc-edge inspect`, then ingest it against the local API with `--data-origin SIMULATED`. This validates the local API/object-storage path without physical hardware.

## Next work

Live ROS 2 subscriptions, selected CDR decoders, normalized sensor adapters, bounded live buffering/backpressure, reconnection, and real-sensor validation remain planned. The gateway remains read-only; robot actuation is outside the initial platform release.
