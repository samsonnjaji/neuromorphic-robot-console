#!/usr/bin/env python3
"""Validate NRC JSON Schemas, contract fixtures and the OpenAPI document."""

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
    (
        ROOT / "schemas/edge-recording-manifest.v1.schema.json",
        ROOT / "schemas/fixtures/edge-recording-manifest.v1.valid.json",
        ROOT / "schemas/fixtures/edge-recording-manifest.v1.invalid.json",
    ),
    (
        ROOT / "schemas/edge-recording-manifest.v2.schema.json",
        ROOT / "schemas/fixtures/edge-recording-manifest.v2.valid.json",
        ROOT / "schemas/fixtures/edge-recording-manifest.v2.invalid.json",
    ),
    (
        ROOT / "schemas/sensor-observation.v1.schema.json",
        ROOT / "schemas/fixtures/sensor-observation.v1.valid.json",
        ROOT / "schemas/fixtures/sensor-observation.v1.invalid.json",
    ),
    (
        ROOT / "schemas/sensor-observation.v1.schema.json",
        ROOT / "schemas/fixtures/sensor-observation.v1.inline.valid.json",
        ROOT / "schemas/fixtures/sensor-observation.v1.invalid.json",
    ),
)
OPENAPI_PATH = ROOT / "docs/api/openapi.json"


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def find_refs(value):
    if isinstance(value, dict):
        for key, child in value.items():
            if key == "$ref" and isinstance(child, str):
                yield child
            else:
                yield from find_refs(child)
    elif isinstance(value, list):
        for child in value:
            yield from find_refs(child)


def validate_openapi() -> None:
    spec = load_json(OPENAPI_PATH)
    if spec.get("openapi") != "3.1.0":
        raise AssertionError("NRC OpenAPI document must use OpenAPI 3.1.0")

    required_paths = {
        "/api/v1/health",
        "/api/v1/experiments",
        "/api/v1/experiments/{experimentId}",
        "/api/v1/robots/{robotId}/telemetry",
    }
    missing = required_paths.difference(spec.get("paths", {}))
    if missing:
        raise AssertionError(f"OpenAPI document is missing paths: {sorted(missing)}")

    for reference in find_refs(spec):
        if reference.startswith("#"):
            continue
        target = (OPENAPI_PATH.parent / reference.split("#", 1)[0]).resolve()
        if not target.is_file():
            raise AssertionError(f"OpenAPI reference does not exist: {reference}")
        if target.suffix == ".json":
            load_json(target)

    websocket = spec.get("x-websocket-endpoints", {})
    if "/api/v1/robots/{robotId}/stream" not in websocket:
        raise AssertionError("OpenAPI document is missing the telemetry WebSocket extension")

    print("PASS docs/api/openapi.json: required REST paths and external schema references exist")


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

    validate_openapi()


if __name__ == "__main__":
    main()
