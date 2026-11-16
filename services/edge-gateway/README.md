# NRC Edge Gateway — MCAP ingestion v1

This package provides NRC's offline MCAP recording importer. It inspects recorded MCAP files and registers the original recording plus a versioned provenance manifest through the existing NRC Platform API.

It is an **offline, read-only recording importer**. It does not yet subscribe to live ROS 2 topics, decode ROS 2 CDR payloads, convert sensor messages into dashboard telemetry, or issue robot commands.

## Capabilities

- Validates MCAP header/footer magic and parses message/channel records.
- Indexes channel IDs, topic names, message encodings, schema names/encodings and channel metadata.
- Counts messages per channel and preserves first/last/min/max sequence values.
- Preserves recorder log timestamps and publisher/source timestamps separately, in nanoseconds.
- Counts zero publisher timestamps separately; it never substitutes a log timestamp for a missing publish timestamp.
- Computes a streaming SHA-256 digest without loading the recording into memory.
- Creates an NRC experiment run and MCAP recording session through the existing API.
- Uploads the original MCAP file and a JSON provenance manifest through the existing single-part or multipart artifact workflow.
- Waits for the API to verify both artifact sizes and SHA-256 checksums before completing the experiment run.

Message payload bytes are not decoded or rewritten. The original MCAP is retained as the authoritative recording artifact.

## Install

Python 3.12 or newer is required.

From the repository root:

```bash
python -m pip install -e services/edge-gateway
```

For tests:

```bash
python -m pip install -e "services/edge-gateway[test]"
python -m unittest discover -s services/edge-gateway/tests -v
```

## Inspect a recording

```bash
nrc-edge inspect ./recording.mcap
```

This validates the file and prints a summary containing the SHA-256 digest, channel/topic inventory, message counts and separate timestamp ranges. It does not contact the Platform API.

## Generate a synthetic local test recording

The repository includes a fixture generator for validating the importer without hardware. Its output is explicitly synthetic and must not be reported as physical sensor data.

```bash
python services/edge-gateway/examples/create_synthetic_mcap.py --output ./synthetic-demo.mcap --messages 10
nrc-edge inspect ./synthetic-demo.mcap
```

To ingest this fixture into a running local NRC API, use `--data-origin SIMULATED`:

```bash
nrc-edge ingest ./synthetic-demo.mcap \
  --api-url http://127.0.0.1:8080 \
  --robot-id nrc-demo-robot \
  --source-id synthetic-fixture \
  --scenario CLEAR \
  --data-origin SIMULATED
```

The CLI reports both artifact IDs and returns success only after the API reports both artifacts `VERIFIED`. This tests the gateway-to-API/object-store path with synthetic data; it does not validate a physical sensor.

## Ingest a recording into NRC

Start the local NRC PostgreSQL and RustFS services and Platform API using [the local development guide](../../deploy/local/README.md). The API must be reachable on loopback and configured with both PostgreSQL and S3-compatible object storage.

```bash
nrc-edge ingest ./recording.mcap \
  --api-url http://127.0.0.1:8080 \
  --robot-id lab-robot-01 \
  --source-id lab-session-2026-11-16 \
  --scenario HEAVY_RAIN \
  --data-origin PHYSICAL_SENSOR
```

If `--experiment-id` is omitted, the gateway creates an experiment with model identity `not-evaluated`, because this workflow imports a recording and does not run perception inference. Supply an existing `--experiment-id` to attach the recording to an existing experiment. The selected scenario must be one of the NRC API's existing values: `CLEAR`, `HEAVY_RAIN`, `FOG`, `LOW_LIGHT` or `DUST_SMOKE`. When attaching to an existing experiment with `--experiment-id`, omit `--scenario`; the existing experiment owns its scenario. `--data-origin` accepts `PHYSICAL_SENSOR`, `SIMULATED`, `DATASET` or `UNKNOWN` and defaults to `UNKNOWN`.

The gateway creates a run, transitions it to `RUNNING`, creates and finalizes an MCAP recording session, uploads the original file and manifest, then marks the run `COMPLETED`. If an upload fails, it attempts to mark the run `FAILED`; the original error is returned to the caller.

## Provenance and timestamps

The versioned contract is [edge-recording-manifest.v1.schema.json](../../schemas/edge-recording-manifest.v1.schema.json).

- MCAP `publish_time` is retained as the source/publisher timestamp.
- MCAP `log_time` is retained as the recorder timestamp.
- Both are represented as Unix nanoseconds; they are not converted to milliseconds.
- A publish timestamp of zero is counted as unavailable and is not replaced.
- Channel metadata, including producer-supplied frame/unit hints, is retained as strings.
- The gateway's `recordedAtUnixMs` is the time the manifest was generated; it is not a sensor timestamp.
- `dataOrigin` is an explicit caller-supplied label: `PHYSICAL_SENSOR`, `SIMULATED`, `DATASET` or `UNKNOWN`. It is never inferred from the MCAP profile. Known NRC synthetic-fixture markers cannot be labelled `PHYSICAL_SENSOR` or `DATASET`; this is an accidental-misclassification guard, not cryptographic origin attestation.
- `ingestionMode` is `RECORDED`, describing the MCAP ingestion path rather than claiming the source was physical.

This version indexes MCAP metadata only. Frame IDs or units embedded inside serialized message payloads are not decoded. They remain in the untouched original MCAP and will be interpreted by a later ROS 2 message-decoding adapter.

## Upload behavior

The gateway follows the API's artifact upload plan:

- `SINGLE_PUT`: streams the file to the presigned upload URL.
- `MULTIPART`: requests one presigned URL per part, streams each bounded part, retains each returned ETag and submits the ordered ETag list.
- Both artifacts are finalized only when the API returns status `VERIFIED`.

The client uses bounded 1 MiB reads while sending each part. It does not print or persist presigned URLs. Presigned URLs are bearer credentials and must remain private.

## Security and safety boundary

- The current Platform API is an unauthenticated local MVP. Keep it bound to loopback; do not expose it to a public or shared network.
- Use only trusted local recordings and a trusted API endpoint.
- The gateway is read-only. There are no ROS 2 publishers, motion commands, motor-control interfaces or robot actuation paths.
- Synthetic, dataset and physical-sensor origins must remain distinguishable. The manifest separates caller-supplied `dataOrigin` from the fixed `ingestionMode: RECORDED`, and explicitly records that inference was not executed.
- A successful import proves file parsing and artifact integrity, not sensor calibration, model accuracy or robot safety.

## Next edge-gateway increments

1. Add ROS 2 topic discovery and explicitly configured read-only subscriptions.
2. Decode selected ROS 2 message types with their declared schemas.
3. Normalize source timestamps, frame IDs and units without silently substituting missing values.
4. Add bounded live-message buffering, explicit backpressure/drop metrics and reconnect handling.
5. Publish compact, validated telemetry snapshots through the existing API while retaining raw recordings as artifacts.
6. Validate the end-to-end path using one real robot or sensor recording.
