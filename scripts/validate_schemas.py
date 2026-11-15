#!/usr/bin/env python3
"""Validate NRC JSON Schemas and positive/negative contract fixtures."""

import json
from pathlib import Path

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]
CASES = (
    (
        ROOT / "schemas/telemetry-envelope.v1.schema.json",
        ROOT / "schemas/fixtures/telemetry-envelope.v1.valid.json",
        ROOT / "schemas/fixtures/telemetry-envelope.v1.invalid.json",
    ),
    (
        ROOT / "schemas/experiment-manifest.v1.schema.json",
        ROOT / "schemas/fixtures/experiment-manifest.v1.valid.json",
        ROOT / "schemas/fixtures/experiment-manifest.v1.invalid.json",
    ),
)


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def main() -> None:
    for schema_path, valid_path, invalid_path in CASES:
        schema = load_json(schema_path)
        Draft202012Validator.check_schema(schema)
        validator = Draft202012Validator(schema)

        valid_errors = list(validator.iter_errors(load_json(valid_path)))
        if valid_errors:
            details = "; ".join(error.message for error in valid_errors)
            raise AssertionError(f"{valid_path.name} should be valid: {details}")

        invalid_errors = list(validator.iter_errors(load_json(invalid_path)))
        if not invalid_errors:
            raise AssertionError(f"{invalid_path.name} should fail schema validation")

        print(
            f"PASS {schema_path.name}: schema valid, "
            f"{valid_path.name} accepted, {invalid_path.name} rejected"
        )


if __name__ == "__main__":
    main()
