# NRC Safety Model

## Safety boundary

NRC is an operator interface and observability tool. It is not a safety-rated controller and must never be the sole mechanism preventing hazardous motion.

## MVP rules

- The first version uses simulated telemetry and simulated commands only.
- The demo speed target is limited to 0.50 m/s in shared application logic.
- Emergency-stop state forces the requested target speed to zero.
- Non-finite and negative speed commands are rejected as zero.
- The UI must clearly indicate when it is in simulation mode.
- Mission replay is read-only: it replays recorded telemetry and does not transmit robot commands.

## Physical robot integration requirements

Before enabling hardware commands:

1. Enforce speed and acceleration limits on the robot/controller, not only in the UI.
2. Require an authenticated and authorised command channel.
3. Use command sequence numbers, timestamps and expiry/timeout handling.
4. Implement a robot-side watchdog that stops motion when commands expire.
5. Preserve a physical emergency-stop circuit independent of NRC and network connectivity.
6. Reject stale, malformed, out-of-range or replayed commands.
7. Log command intent, acceptance/rejection and resulting robot state.
8. Test loss of network, process crash, low battery and sensor failure before field operation.
9. Require an explicit operator mode transition before a hardware transport can accept motion commands.
