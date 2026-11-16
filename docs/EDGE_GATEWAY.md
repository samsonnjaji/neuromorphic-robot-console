# NRC Edge Gateway

The first edge-gateway increment is an offline MCAP recording importer implemented in `services/edge-gateway`.

It validates MCAP files, indexes channel/topic/schema metadata, records message and sequence counts, preserves publisher and recorder timestamps separately in nanoseconds, computes SHA-256, and uploads the original recording plus a versioned provenance manifest through the existing NRC Platform API and artifact store.

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

## Timestamp policy

MCAP `publish_time` and `log_time` are separate nanosecond values. A zero publish timestamp is counted as unavailable; it is never silently replaced with the recorder timestamp. The manifest-generation time is separately labelled in Unix milliseconds.

## Next work

Live ROS 2 subscriptions, selected CDR decoders, normalized sensor adapters, bounded live buffering/backpressure, reconnection, and real-sensor validation remain planned. The gateway remains read-only; robot actuation is outside the initial platform release.
