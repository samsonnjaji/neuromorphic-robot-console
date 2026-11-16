"""Bounded-memory inspection of MCAP recordings without decoding payloads."""

from __future__ import annotations

import hashlib
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from mcap.reader import make_reader

from . import __version__

MCAP_MAGIC = b"\x89MCAP0\r\n"
HASH_CHUNK_BYTES = 1024 * 1024


class McapValidationError(ValueError):
    """Raised when a file is not a complete, readable MCAP recording."""


@dataclass
class _ChannelAccumulator:
    channel_id: int
    topic: str
    message_encoding: str
    schema_name: str | None
    schema_encoding: str | None
    metadata: dict[str, str]
    message_count: int = 0
    min_publish_time_unix_ns: int | None = None
    max_publish_time_unix_ns: int | None = None
    publish_time_zero_count: int = 0
    min_log_time_unix_ns: int | None = None
    max_log_time_unix_ns: int | None = None
    first_sequence: int | None = None
    last_sequence: int | None = None
    min_sequence: int | None = None
    max_sequence: int | None = None

    def add(self, log_time: int, publish_time: int, sequence: int) -> None:
        self.message_count += 1
        self.min_log_time_unix_ns = _minimum(self.min_log_time_unix_ns, log_time)
        self.max_log_time_unix_ns = _maximum(self.max_log_time_unix_ns, log_time)

        # MCAP uses publish_time=0 when a publisher timestamp is unavailable.
        # Keep that sentinel count separate; never replace it with log_time.
        if publish_time == 0:
            self.publish_time_zero_count += 1
        else:
            self.min_publish_time_unix_ns = _minimum(
                self.min_publish_time_unix_ns, publish_time
            )
            self.max_publish_time_unix_ns = _maximum(
                self.max_publish_time_unix_ns, publish_time
            )

        if self.first_sequence is None:
            self.first_sequence = sequence
        self.last_sequence = sequence
        self.min_sequence = _minimum(self.min_sequence, sequence)
        self.max_sequence = _maximum(self.max_sequence, sequence)

    def to_dict(self) -> dict[str, Any]:
        return {
            "channelId": self.channel_id,
            "topic": self.topic,
            "messageEncoding": self.message_encoding,
            "schemaName": self.schema_name,
            "schemaEncoding": self.schema_encoding,
            "channelMetadata": dict(sorted(self.metadata.items())),
            "messageCount": self.message_count,
            "minPublishTimeUnixNs": self.min_publish_time_unix_ns,
            "maxPublishTimeUnixNs": self.max_publish_time_unix_ns,
            "publishTimeZeroCount": self.publish_time_zero_count,
            "minLogTimeUnixNs": self.min_log_time_unix_ns,
            "maxLogTimeUnixNs": self.max_log_time_unix_ns,
            "firstSequence": self.first_sequence,
            "lastSequence": self.last_sequence,
            "minSequence": self.min_sequence,
            "maxSequence": self.max_sequence,
        }


@dataclass(frozen=True)
class McapInspection:
    file_name: str
    file_size_bytes: int
    sha256_hex: str
    profile: str
    library: str
    message_count: int
    channels: tuple[dict[str, Any], ...]
    min_log_time_unix_ns: int
    max_log_time_unix_ns: int
    min_publish_time_unix_ns: int | None
    max_publish_time_unix_ns: int | None
    publish_time_zero_count: int

    @property
    def topic_count(self) -> int:
        return len({channel["topic"] for channel in self.channels})

    @property
    def channel_count(self) -> int:
        return len(self.channels)

    def summary(self) -> dict[str, Any]:
        """Return a concise inspection result without robot or experiment identity."""
        return {
            "fileName": self.file_name,
            "fileSizeBytes": self.file_size_bytes,
            "sha256Hex": self.sha256_hex,
            "mcapProfile": self.profile,
            "mcapLibrary": self.library,
            "messageCount": self.message_count,
            "topicCount": self.topic_count,
            "channelCount": self.channel_count,
            "timestamps": {
                "unit": "nanoseconds",
                "logTimeUnixNs": {
                    "min": self.min_log_time_unix_ns,
                    "max": self.max_log_time_unix_ns,
                },
                "publishTimeUnixNs": {
                    "min": self.min_publish_time_unix_ns,
                    "max": self.max_publish_time_unix_ns,
                    "zeroCount": self.publish_time_zero_count,
                },
            },
            "channels": list(self.channels),
        }

    def manifest(
        self,
        robot_id: str,
        source_id: str | None = None,
        recorded_at_unix_ms: int | None = None,
    ) -> dict[str, Any]:
        """Build the versioned provenance manifest for an NRC recording session."""
        normalized_robot_id = robot_id.strip()
        normalized_source_id = (
            source_id.strip()
            if source_id is not None and source_id.strip()
            else normalized_robot_id
        )
        if not normalized_robot_id:
            raise ValueError("robot_id must not be blank")
        if len(normalized_robot_id) > 255 or len(normalized_source_id) > 255:
            raise ValueError("robot_id and source_id must be at most 255 characters")

        return {
            "schemaVersion": 1,
            "gatewayVersion": __version__,
            "recordedAtUnixMs": (
                recorded_at_unix_ms
                if recorded_at_unix_ms is not None
                else time.time_ns() // 1_000_000
            ),
            "dataOrigin": "RECORDED",
            "robotId": normalized_robot_id,
            "sourceId": normalized_source_id,
            "fileName": self.file_name,
            "fileSizeBytes": self.file_size_bytes,
            "sha256Hex": self.sha256_hex,
            "mcapProfile": self.profile,
            "mcapLibrary": self.library,
            "messageCount": self.message_count,
            "topicCount": self.topic_count,
            "channelCount": self.channel_count,
            "timestamps": {
                "unit": "nanoseconds",
                "logTimeUnixNs": {
                    "min": self.min_log_time_unix_ns,
                    "max": self.max_log_time_unix_ns,
                },
                "publishTimeUnixNs": {
                    "min": self.min_publish_time_unix_ns,
                    "max": self.max_publish_time_unix_ns,
                    "zeroCount": self.publish_time_zero_count,
                },
            },
            "channels": list(self.channels),
        }


class McapInspector:
    """Inspect MCAP structure and timestamps while leaving payload bytes untouched."""

    def inspect(self, file_path: str | Path) -> McapInspection:
        path = Path(file_path).expanduser()
        if not path.is_file():
            raise McapValidationError(f"MCAP file does not exist or is not a file: {path.name}")

        file_size = path.stat().st_size
        if file_size < len(MCAP_MAGIC) * 2:
            raise McapValidationError("MCAP file is too small to contain header and footer magic")

        with path.open("rb") as stream:
            if stream.read(len(MCAP_MAGIC)) != MCAP_MAGIC:
                raise McapValidationError("File does not begin with MCAP magic bytes")
            stream.seek(-len(MCAP_MAGIC), 2)
            if stream.read(len(MCAP_MAGIC)) != MCAP_MAGIC:
                raise McapValidationError("File is truncated or missing MCAP footer magic")

        channels: dict[int, _ChannelAccumulator] = {}
        message_count = 0
        min_log_time: int | None = None
        max_log_time: int | None = None
        min_publish_time: int | None = None
        max_publish_time: int | None = None
        publish_time_zero_count = 0
        profile = ""
        library = ""

        try:
            with path.open("rb") as stream:
                reader = make_reader(stream, validate_crcs=True)
                header = reader.get_header() if hasattr(reader, "get_header") else None
                summary = reader.get_summary() if hasattr(reader, "get_summary") else None
                profile = str(
                    getattr(header, "profile", None)
                    or getattr(summary, "profile", "")
                    or ""
                )
                library = str(
                    getattr(header, "library", None)
                    or getattr(summary, "library", "")
                    or ""
                )

                # Preserve file order and stream records; log-time sorting can materialize an entire
                # unchunked recording in memory in the MCAP reader implementation.
                for schema, channel, message in reader.iter_messages(log_time_order=False):
                    channel_id = int(channel.id)
                    accumulator = channels.get(channel_id)
                    if accumulator is None:
                        metadata = getattr(channel, "metadata", None) or {}
                        accumulator = _ChannelAccumulator(
                            channel_id=channel_id,
                            topic=str(channel.topic),
                            message_encoding=str(channel.message_encoding),
                            schema_name=(
                                str(schema.name) if schema is not None else None
                            ),
                            schema_encoding=(
                                str(schema.encoding) if schema is not None else None
                            ),
                            metadata={
                                str(key): str(value)
                                for key, value in metadata.items()
                            },
                        )
                        channels[channel_id] = accumulator

                    log_time = int(message.log_time)
                    publish_time = int(message.publish_time)
                    sequence = int(message.sequence)
                    accumulator.add(log_time, publish_time, sequence)

                    message_count += 1
                    min_log_time = _minimum(min_log_time, log_time)
                    max_log_time = _maximum(max_log_time, log_time)
                    if publish_time == 0:
                        publish_time_zero_count += 1
                    else:
                        min_publish_time = _minimum(min_publish_time, publish_time)
                        max_publish_time = _maximum(max_publish_time, publish_time)
        except McapValidationError:
            raise
        except Exception as failure:
            raise McapValidationError(
                f"MCAP reader could not parse {path.name}: {failure}"
            ) from failure

        if message_count == 0 or not channels:
            raise McapValidationError("MCAP recording contains no messages")

        return McapInspection(
            file_name=path.name,
            file_size_bytes=file_size,
            sha256_hex=_sha256(path),
            profile=profile,
            library=library,
            message_count=message_count,
            channels=tuple(
                accumulator.to_dict()
                for _, accumulator in sorted(channels.items())
            ),
            min_log_time_unix_ns=int(min_log_time or 0),
            max_log_time_unix_ns=int(max_log_time or 0),
            min_publish_time_unix_ns=min_publish_time,
            max_publish_time_unix_ns=max_publish_time,
            publish_time_zero_count=publish_time_zero_count,
        )


def _minimum(current: int | None, value: int) -> int:
    return value if current is None else min(current, value)


def _maximum(current: int | None, value: int) -> int:
    return value if current is None else max(current, value)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(HASH_CHUNK_BYTES):
            digest.update(chunk)
    return digest.hexdigest()
