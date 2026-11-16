from __future__ import annotations

import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator


class SensorObservationContractTests(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(__file__).resolve().parents[3]
        self.schema = json.loads(
            (self.root / "schemas/sensor-observation.v1.schema.json").read_text(
                encoding="utf-8"
            )
        )
        self.validator = Draft202012Validator(self.schema)

    def test_mcap_reference_fixture_is_valid(self) -> None:
        fixture = json.loads(
            (
                self.root
                / "schemas/fixtures/sensor-observation.v1.valid.json"
            ).read_text(encoding="utf-8")
        )
        self.validator.validate(fixture)

    def test_inline_json_fixture_is_valid(self) -> None:
        fixture = json.loads(
            (
                self.root
                / "schemas/fixtures/sensor-observation.v1.inline.valid.json"
            ).read_text(encoding="utf-8")
        )
        self.validator.validate(fixture)

    def test_missing_source_timestamp_requires_unknown_clock(self) -> None:
        fixture = json.loads(
            (
                self.root
                / "schemas/fixtures/sensor-observation.v1.invalid.json"
            ).read_text(encoding="utf-8")
        )
        with self.assertRaises(Exception):
            self.validator.validate(fixture)


if __name__ == "__main__":
    unittest.main()
