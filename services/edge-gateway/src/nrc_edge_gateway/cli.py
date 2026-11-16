"""Command-line entry point for NRC offline MCAP ingestion."""

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

SCENARIOS = ("CLEAR", "HEAVY_RAIN", "FOG", "LOW_LIGHT", "DUST_SMOKE")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="nrc-edge",
        description="Inspect and ingest recorded MCAP data into NRC (read-only).",
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
    ingest_command.add_argument("--api-url", default=os.getenv("NRC_API_BASE_URL", "http://127.0.0.1:8080"))
    ingest_command.add_argument("--timeout-seconds", type=float, default=60.0)
    ingest_command.add_argument("--robot-id", required=True)
    ingest_command.add_argument("--source-id")
    ingest_command.add_argument("--scenario", required=True, choices=SCENARIOS)
    ingest_command.add_argument("--experiment-id")
    ingest_command.add_argument("--experiment-name")
    ingest_command.add_argument("--description", default="")
    ingest_command.add_argument("--model-identifier", default="not-evaluated")
    ingest_command.add_argument("--model-version", default="not-evaluated")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    arguments = parser.parse_args(argv)

    try:
        if arguments.command == "inspect":
            result = McapInspector().inspect(arguments.file)
            print(json.dumps(result.summary(), indent=2, sort_keys=True))
            return 0

        api = NrcApiClient(
            arguments.api_url, timeout_seconds=arguments.timeout_seconds
        )
        result = NrcMcapIngestor(api).ingest(
            file_path=arguments.file,
            robot_id=arguments.robot_id,
            source_id=arguments.source_id,
            scenario=arguments.scenario,
            experiment_id=arguments.experiment_id,
            experiment_name=arguments.experiment_name,
            description=arguments.description,
            model_identifier=arguments.model_identifier,
            model_version=arguments.model_version,
        )
        print(json.dumps(result.to_dict(), indent=2, sort_keys=True))
        return 0
    except (McapValidationError, NrcApiError, OSError, ValueError) as failure:
        print(f"nrc-edge: {failure}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
