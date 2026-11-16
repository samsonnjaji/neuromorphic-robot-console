#!/usr/bin/env python3
"""Create an explicitly synthetic MCAP fixture for local NRC smoke testing."""

from __future__ import annotations

import argparse
from pathlib import Path

from nrc_edge_gateway.synthetic_fixture import create_synthetic_mcap


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("synthetic-demo.mcap"),
        help="output MCAP path (default: ./synthetic-demo.mcap)",
    )
    parser.add_argument("--messages", type=int, default=10)
    arguments = parser.parse_args()

    output = create_synthetic_mcap(arguments.output, arguments.messages)
    print(f"SYNTHETIC fixture created: {output}")
    print("This file is generated test data, not a physical sensor recording.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
