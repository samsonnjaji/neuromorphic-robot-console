"""Generate clearly labelled synthetic ROS 2-style MCAP data for local smoke tests."""

from __future__ import annotations

import struct
import time
from pathlib import Path

from mcap.writer import CompressionType, Writer

from . import __version__


def create_synthetic_mcap(
    file_path: str | Path,
    message_count: int = 10,
) -> Path:
    """Create a small ROS 2-profile MCAP fixture; this is not physical sensor data."""
    if message_count < 1 or message_count > 10_000:
        raise ValueError("message_count must be between 1 and 10000")

    path = Path(file_path).expanduser()
    path.parent.mkdir(parents=True, exist_ok=True)
    base_time_ns = time.time_ns()

    with path.open("wb") as output:
        writer = Writer(output, compression=CompressionType.NONE)
        writer.start(
            profile="ros2",
            library=f"nrc-edge-gateway/{__version__}-synthetic-fixture",
        )
        schema_id = writer.register_schema(
            name="std_msgs/msg/String",
            encoding="ros2msg",
            data=b"string data\n",
        )
        channel_id = writer.register_channel(
            topic="/nrc/synthetic_status",
            message_encoding="cdr",
            schema_id=schema_id,
            metadata={
                "data_origin": "SIMULATED",
                "fixture": "synthetic",
                "frame_id": "base_link",
                "unit": "none",
            },
        )

        for sequence in range(1, message_count + 1):
            publish_time_ns = base_time_ns + sequence * 10_000_000
            log_time_ns = publish_time_ns + 100_000
            payload = _serialize_cdr_string(f"nrc-synthetic-frame-{sequence}")
            writer.add_message(
                channel_id,
                log_time=log_time_ns,
                publish_time=publish_time_ns,
                sequence=sequence,
                data=payload,
            )
        writer.finish()

    return path


def _serialize_cdr_string(value: str) -> bytes:
    """Serialize std_msgs/msg/String as little-endian CDR with encapsulation."""
    encoded = value.encode("utf-8") + b"\x00"
    encapsulation = b"\x00\x01\x00\x00"
    return encapsulation + struct.pack("<I", len(encoded)) + encoded
