# ADR-003: Separate sensor observations from aggregated robot telemetry

- **Status:** Accepted
- **Date:** 2026-11-16

## Context

NRC's existing telemetry envelope and shared RobotTelemetry model describe an aggregated robot-state snapshot. A raw ROS 2 message such as sensor_msgs/msg/Imu, sensor_msgs/msg/Image, sensor_msgs/msg/LaserScan or nav_msgs/msg/Odometry does not contain all of those snapshot fields. Filling absent fields with zeroes or guessed values would create false measurements and damage scientific validity.

The edge gateway also needs to preserve source timestamps, reception timestamps, frame identifiers, units, message type and raw payload provenance without forcing large binary data through JSON or relational rows.

## Decision

Introduce a separate, versioned Sensor Observation Envelope v1.

Each observation identifies one robot, source, topic, message type, per-topic gateway sequence, source timestamp and clock domain, reception timestamp, optional frame ID, explicit unit labels, data origin and payload representation.

Payloads may be inline base64/JSON for small values or reference an MCAP artifact by artifact ID, channel ID and message index for raw/high-bandwidth data.

The existing telemetry snapshot contract remains unchanged. The initial ROS 2 capture path uses the official rosbag2 MCAP storage plugin with an explicit topic allowlist. The gateway remains read-only.

## Consequences

- Raw sensor observations are no longer coerced into a complete RobotTelemetry snapshot.
- Source and reception clocks remain distinct.
- Missing frame IDs, units or source timestamps remain missing/unknown rather than being fabricated.
- MCAP remains the durable source for raw payload bytes.
- A future API route and typed adapters can be added without breaking existing dashboard clients.
- This schema does not itself decode CDR or prove that a claimed data origin is authentic.
