# NRC Sensor Observation Envelope v1

## Purpose

The existing telemetry envelope describes an aggregated robot-state snapshot for the dashboard. A raw ROS 2 sensor message is not necessarily such a snapshot. NRC must not fabricate battery, velocity, detection, LiDAR, event-camera or SNN fields merely to fit an IMU, image, point-cloud or odometry message into that model.

The versioned Sensor Observation Envelope represents one observation from one configured topic. It is a separate contract; it does not replace or silently change the existing telemetry snapshot.

## Required semantics

- `topic` and `messageTypeName` identify the ROS 2 stream and message type.
- `sequence` is the gateway's monotonically increasing per-topic observation index, not an assumed source-provided sequence number.
- `sourceTimestampUnixNs` preserves the source/publisher timestamp when present. It is null when the source message has no usable timestamp.
- `sourceClock` records the source clock domain. A null source timestamp requires `UNKNOWN`.
- `receptionTimestampUnixNs` is the gateway's wall-clock reception time. It must never be silently substituted into the source timestamp.
- `frameId` is nullable; do not invent a frame when the message has none.
- `units` contains explicit unit labels only. Unknown units remain absent.
- `dataOrigin` is an explicit provenance label: PHYSICAL_SENSOR, SIMULATED, DATASET or UNKNOWN.
- `payload` is either inline base64/JSON for small observations or an MCAP artifact reference for large/raw data.

## MCAP references

For recorded or high-bandwidth data, keep the original CDR bytes in the MCAP artifact. A Sensor Observation can reference the artifact ID, MCAP channel ID and zero-based message index. This avoids base64 expansion and prevents the relational API from becoming a raw image/point-cloud store.

The observation envelope does not claim that the referenced payload has been decoded. Decoding requires a known message schema and a validated adapter.

## Relationship to current NRC components

- Existing `RobotTelemetry` and `telemetry-envelope.v1` remain unchanged.
- `sensor-observation.v1` is a separate versioned contract for per-topic data.
- `edge-recording-manifest.v2` records file-level provenance and MCAP channel summaries.
- Artifact storage remains the source of truth for raw recordings.
- No API ingestion route is added by this contract-only increment.

## Safety and research integrity

The ROS 2 gateway is read-only. It records explicitly allowlisted topics and does not publish commands or actuate hardware. Synthetic, dataset and physical sensor origins must remain distinct. Timestamp units, frame IDs and payload encoding must be preserved rather than inferred.
