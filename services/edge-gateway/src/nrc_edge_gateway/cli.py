"""Command-line entry point for NRC MCAP inspection, ingestion and ROS 2 capture."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Sequence

from .api_client import NrcApiClient, NrcApiError
from .ingest import NrcMcapIngestor
from .mcap_inspector import McapInspector, McapValidationError
from .ros2_capture import (
    DEFAULT_MAX_CACHE_SIZE_BYTES,
    Ros2BagRecorder,
    Ros2CaptureError,
    Ros2RecordOptions,
)

SCENARIOS = ("CLEAR", "HEAVY_RAIN", "FOG", "LOW_LIGHT", "DUST_SMOKE")
DATA_ORIGINS = ("PHYSICAL_SENSOR", "SIMULATED", "DATASET", "UNKNOWN")


def _add_ingest_options(command: argparse.ArgumentParser) -> None:
    command.add_argument(
        "--api-url",
        default=os.getenv("NRC_API_BASE_URL", "http://127.0.0.1:8080"),
    )
    command.add_argument("--timeout-seconds", type=float, default=60.0)
    command.add_argument("--robot-id", required=True)
    command.add_argument("--source-id")
    command.add_argument(
        "--scenario",
        choices=SCENARIOS,
        help="required when creating a new experiment; omit with --experiment-id",
    )
    command.add_argument(
        "--data-origin",
        choices=DATA_ORIGINS,
        default="UNKNOWN",
        help="source provenance; defaults to UNKNOWN rather than assuming physical data",
    )
    command.add_argument("--experiment-id")
    command.add_argument("--experiment-name")
    command.add_argument("--description", default="")
    command.add_argument("--model-identifier", default="not-evaluated")
    command.add_argument("--model-version", default="not-evaluated")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="nrc-edge",
        description="Inspect and ingest recorded MCAP data into NRC; optionally capture ROS 2 topics read-only.",
    )
    commands = parser.add_subparsers(dest="command", required=True)

    inspect_command = commands.add_parser(
        "inspect", help="validate an MCAP file and print its provenance summary"
    )
    inspect_command.add_argument("file", type=Path)

    ingest_command = commands.add_parser(
        "ingest", help="register a recording and upload the MCAP plus manifest"
    )
    ingest_command.add_argument("file", type=Path)
    _add_ingest_options(ingest_command)

    capture_command = commands.add_parser(
        "ros2-capture",
        help="record an explicit ROS 2 topic allowlist to MCAP, then ingest it into NRC",
    )
    capture_command.add_argument(
        "--topics",
        nargs="+",
        required=True,
        help="explicit absolute ROS 2 topic allowlist; wildcard/all-topic capture is not supported",
    )
    capture_command.add_argument(
        "--output",
        type=Path,
        required=True,
        help="new output directory for the rosbag2 MCAP recording; it must not already exist",
    )
    capture_command.add_argument(
        "--duration-seconds",
        type=float,
        help="stop and finalize capture after this duration; otherwise press Ctrl+C",
    )
    capture_command.add_argument(
        "--max-cache-size-bytes",
        type=int,
        default=DEFAULT_MAX_CACHE_SIZE_BYTES,
        help="rosbag2 per-buffer cache bound in bytes (default 64 MiB; double buffering may use up to twice this)",
    )
    capture_command.add_argument(
        "--qos-profile-overrides-path",
        type=Path,
        help="optional ROS 2 QoS override YAML file",
    )
    capture_command.add_argument(
        "--use-sim-time",
        action="store_true",
        help="use ROS simulation time from /clock; recording waits for the first clock message",
    )
    capture_command.add_argument(
        "--ros2-executable",
        default="ros2",
        help="ROS 2 CLI executable (default: ros2)",
    )
    _add_ingest_options(capture_command)
    return parser


def _capture_ros2(arguments: argparse.Namespace) -> int:
    if arguments.experiment_id is None and arguments.scenario is None:
        raise ValueError("--scenario is required when creating a new experiment")
    if arguments.experiment_id is not None and arguments.scenario is not None:
        raise ValueError("omit --scenario with --experiment-id; the existing experiment owns it")

    source_id_base = (arguments.source_id or arguments.robot_id).strip()
    if not source_id_base or len(source_id_base) > 255:
        raise ValueError("source-id must contain between 1 and 255 characters")

    options = Ros2RecordOptions(
        topics=tuple(arguments.topics),
        output_directory=arguments.output,
        max_cache_size_bytes=arguments.max_cache_size_bytes,
        qos_profile_overrides_path=arguments.qos_profile_overrides_path,
        use_sim_time=arguments.use_sim_time,
        ros2_executable=arguments.ros2_executable,
    )
    mcap_files = Ros2BagRecorder().record(
        options,
        duration_seconds=arguments.duration_seconds,
    )

    api = NrcApiClient(
        arguments.api_url,
        timeout_seconds=arguments.timeout_seconds,
    )
    ingestor = NrcMcapIngestor(api)
    experiment_id = arguments.experiment_id
    ingestions: list[dict[str, object]] = []

    for index, mcap_file in enumerate(mcap_files):
        source_id = source_id_base
        if len(mcap_files) > 1:
            source_id = f"{source_id_base[:240]}-segment-{index + 1:03d}"
        scenario = arguments.scenario if experiment_id is None else None
        result = ingestor.ingest(
            file_path=mcap_file,
            robot_id=arguments.robot_id,
            source_id=source_id,
            scenario=scenario,
            data_origin=arguments.data_origin,
            experiment_id=experiment_id,
            experiment_name=arguments.experiment_name,
            description=arguments.description,
            model_identifier=arguments.model_identifier,
            model_version=arguments.model_version,
        )
        experiment_id = result.experiment_id
        ingestions.append(result.to_dict())

    print(
        json.dumps(
            {
                "captureDirectory": str(arguments.output.expanduser().resolve()),
                "topicAllowlist": arguments.topics,
                "mcapFiles": [str(path) for path in mcap_files],
                "experimentId": experiment_id,
                "ingestions": ingestions,
            },
            indent=2,
            sort_keys=True,
        )
    )
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    arguments = parser.parse_args(argv)

    try:
        if arguments.command == "inspect":
            result = McapInspector().inspect(arguments.file)
            print(json.dumps(result.summary(), indent=2, sort_keys=True))
            return 0

        if arguments.command == "ros2-capture":
            return _capture_ros2(arguments)

        api = NrcApiClient(
            arguments.api_url, timeout_seconds=arguments.timeout_seconds
        )
        result = NrcMcapIngestor(api).ingest(
            file_path=arguments.file,
            robot_id=arguments.robot_id,
            source_id=arguments.source_id,
            scenario=arguments.scenario,
            data_origin=arguments.data_origin,
            experiment_id=arguments.experiment_id,
            experiment_name=arguments.experiment_name,
            description=arguments.description,
            model_identifier=arguments.model_identifier,
            model_version=arguments.model_version,
        )
        print(json.dumps(result.to_dict(), indent=2, sort_keys=True))
        return 0
    except (
        McapValidationError,
        NrcApiError,
        Ros2CaptureError,
        OSError,
        ValueError,
    ) as failure:
        print(f"nrc-edge: {failure}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
