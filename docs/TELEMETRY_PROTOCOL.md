# NRC Telemetry Protocol (Design Draft)

This document defines the application-level contract for the future ROS 2 gateway. The current MVP uses the in-process simulator and does not open a network socket.

## Transport boundary

- WebSocket over TLS (`wss://`) is the intended transport for networked robot sessions.
- JSON is the first wire format; binary payloads may be introduced for high-rate event-camera data.
- A separate gateway translates ROS 2 messages into NRC's stable application schema.
- The shared Kotlin application must not depend directly on ROS 2 middleware.
- The Kotlin `TelemetryTransport` interface separates transport lifecycle from UI and domain logic.

## Message envelope

Every telemetry message includes:

```json
{
  "schemaVersion": 1,
  "messageType": "telemetry.snapshot",
  "robotId": "robot-01",
  "sequence": 1842,
  "timestampUnixMs": 1794744000000,
  "payload": {}
}
```

The shared model is `TelemetryEnvelope`. The gateway decodes the wire payload into a typed `RobotTelemetry` before publishing it to the application.

- `schemaVersion` must match a version supported by the client.
- `messageType` identifies the envelope purpose.
- `robotId` must match the robot selected for the session.
- `sequence` is monotonically increasing per robot stream.
- `timestampUnixMs` is the source timestamp, not the client receive time.

## Validation before state update

`TelemetryStreamGuard` rejects unsupported schema versions, unsupported message types, mismatched robot IDs, invalid timestamps, and duplicate or out-of-order sequence numbers. A new guard is created for each robot connection. The last accepted sequence advances only when an envelope passes validation.

The guard is a data-integrity check, not a security boundary. Network authentication, authorisation, TLS configuration and payload validation remain responsibilities of the gateway and transport implementation.

## Telemetry snapshot payload

The first payload groups data into these domains:

- `state`: connection, battery, operating mode and mission state.
- `motion`: odometry pose, linear/angular velocity and IMU.
- `sensors.eventCamera`: dimensions, event timestamp window and event samples.
- `sensors.lidar`: frame, angular increment and range samples.
- `perception`: detections, model identifier, confidence and inference latency.
- `neuromorphic`: network identifier and per-layer neuron/spike statistics.
- `health`: CPU/GPU load, temperature, dropped frames and transport latency.

Units must be explicit in field names or schema documentation. Angles use radians internally unless a field name explicitly states degrees.

## Future command request (disabled in current MVP)

```json
{
  "schemaVersion": 1,
  "messageType": "command.motion.request",
  "robotId": "robot-01",
  "commandId": "cmd-unique-id",
  "sequence": 52,
  "issuedAtUnixMs": 1794744000000,
  "expiresAtUnixMs": 1794744000200,
  "payload": {
    "linearVelocityMps": 0.1,
    "angularVelocityRadS": 0.0
  }
}
```

The gateway must authenticate the operator, authorise the requested operation, reject stale or duplicate commands, enforce allowlisted limits and return an explicit acknowledgement. The robot-side controller must independently validate every command and enforce its watchdog and emergency-stop circuit. NRC's UI-level safety envelope is not sufficient for physical safety.

## Reconnection and data quality

- Reconnect with bounded exponential backoff and jitter.
- Expose connection state and last-received timestamp to the UI.
- Detect sequence gaps and report dropped messages.
- Reject unsupported schema versions rather than silently interpreting unknown fields.
- Keep telemetry read-only until a separate command-security review is complete.
