from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator
from mcap.writer import CompressionType, Writer

from nrc_edge_gateway.mcap_inspector import McapInspector, McapValidationError


def write_fixture(path: Path) -> None:
    with path.open("wb") as output:
        writer = Writer(output, compression=CompressionType.NONE)
        writer.start(profile="ros2", library="nrc-edge-gateway-tests")
        schema_id = writer.register_schema(
            name="std_msgs/msg/String",
            encoding="ros2msg",
            data=b"string data\n",
        )
        channel_id = writer.register_channel(
            topic="/robot/state",
            message_encoding="cdr",
            schema_id=schema_id,
            metadata={"frame_id": "base_link", "unit": "m"},
        )
        writer.add_message(
            channel_id,
            log_time=1_700_000_000_000_000_100,
            publish_time=1_700_000_000_000_000_090,
            sequence=7,
            data=b"fixture-one",
        )
        writer.add_message(
            channel_id,
            log_time=1_700_000_000_000_000_200,
            publish_time=1_700_000_000_000_000_190,
            sequence=8,
            data=b"fixture-two",
        )
        writer.finish()


class McapInspectorTests(unittest.TestCase):
    def test_inspects_channels_sequences_timestamps_and_hash(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.mcap"
            write_fixture(path)

            result = McapInspector().inspect(path)

            self.assertEqual(result.message_count, 2)
            self.assertEqual(result.topic_count, 1)
            self.assertEqual(result.channel_count, 1)
            self.assertEqual(result.profile, "ros2")
            self.assertEqual(result.library, "nrc-edge-gateway-tests")
            self.assertEqual(
                result.sha256_hex,
                hashlib.sha256(path.read_bytes()).hexdigest(),
            )
            channel = result.channels[0]
            self.assertEqual(channel["topic"], "/robot/state")
            self.assertEqual(channel["messageEncoding"], "cdr")
            self.assertEqual(channel["schemaName"], "std_msgs/msg/String")
            self.assertEqual(channel["schemaEncoding"], "ros2msg")
            self.assertEqual(channel["channelMetadata"]["frame_id"], "base_link")
            self.assertEqual(channel["firstSequence"], 7)
            self.assertEqual(channel["lastSequence"], 8)
            self.assertEqual(
                result.min_publish_time_unix_ns, 1_700_000_000_000_000_090
            )
            self.assertEqual(
                result.max_log_time_unix_ns, 1_700_000_000_000_000_200
            )

    def test_zero_publish_time_is_counted_not_replaced_by_log_time(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "zero-publish.mcap"
            with path.open("wb") as output:
                writer = Writer(output, compression=CompressionType.NONE)
                writer.start(profile="ros2", library="test")
                channel_id = writer.register_channel(
                    topic="/robot/imu", message_encoding="cdr", schema_id=0
                )
                writer.add_message(
                    channel_id,
                    log_time=1_700_000_000_000_000_100,
                    publish_time=0,
                    sequence=1,
                    data=b"payload",
                )
                writer.finish()

            result = McapInspector().inspect(path)
            self.assertIsNone(result.min_publish_time_unix_ns)
            self.assertIsNone(result.max_publish_time_unix_ns)
            self.assertEqual(result.publish_time_zero_count, 1)
            self.assertEqual(result.min_log_time_unix_ns, 1_700_000_000_000_000_100)

    def test_generated_manifest_matches_versioned_schema(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.mcap"
            write_fixture(path)
            manifest = McapInspector().inspect(path).manifest(
                robot_id="robot-01",
                source_id="lab-source",
                recorded_at_unix_ms=1_794_810_000_000,
            )
            schema_path = (
                Path(__file__).resolve().parents[3]
                / "schemas/edge-recording-manifest.v1.schema.json"
            )
            schema = json.loads(schema_path.read_text(encoding="utf-8"))
            Draft202012Validator(schema).validate(manifest)

    def test_unchunked_messages_are_read_in_file_order(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "unchunked.mcap"
            with path.open("wb") as output:
                writer = Writer(
                    output,
                    compression=CompressionType.NONE,
                    use_chunking=False,
                )
                writer.start(profile="ros2", library="file-order-test")
                channel_id = writer.register_channel(
                    topic="/robot/pose", message_encoding="cdr", schema_id=0
                )
                writer.add_message(
                    channel_id,
                    log_time=200,
                    publish_time=190,
                    sequence=9,
                    data=b"later-log-time",
                )
                writer.add_message(
                    channel_id,
                    log_time=100,
                    publish_time=90,
                    sequence=10,
                    data=b"earlier-log-time",
                )
                writer.finish()

            result = McapInspector().inspect(path)
            channel = result.channels[0]
            self.assertEqual(channel["firstSequence"], 9)
            self.assertEqual(channel["lastSequence"], 10)
            self.assertEqual(result.min_log_time_unix_ns, 100)
            self.assertEqual(result.max_log_time_unix_ns, 200)

    def test_rejects_non_mcap_input(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "not-mcap.bin"
            path.write_bytes(b"not an MCAP recording")
            with self.assertRaises(McapValidationError):
                McapInspector().inspect(path)


if __name__ == "__main__":
    unittest.main()
