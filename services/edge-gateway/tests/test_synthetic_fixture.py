from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator

from nrc_edge_gateway.mcap_inspector import McapInspector
from nrc_edge_gateway.synthetic_fixture import create_synthetic_mcap


class SyntheticFixtureTests(unittest.TestCase):
    def test_fixture_is_marked_simulated_and_matches_manifest_contract(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = create_synthetic_mcap(
                Path(directory) / "synthetic.mcap",
                message_count=3,
            )
            inspection = McapInspector().inspect(path)
            manifest = inspection.manifest(
                robot_id="nrc-demo-robot",
                source_id="synthetic-fixture",
                data_origin="SIMULATED",
                recorded_at_unix_ms=1_794_810_000_000,
            )

            self.assertEqual(inspection.message_count, 3)
            self.assertEqual(inspection.profile, "ros2")
            self.assertEqual(manifest["dataOrigin"], "SIMULATED")
            self.assertEqual(manifest["ingestionMode"], "RECORDED")
            self.assertEqual(
                manifest["channels"][0]["channelMetadata"]["data_origin"],
                "SIMULATED",
            )

            schema_path = (
                Path(__file__).resolve().parents[3]
                / "schemas/edge-recording-manifest.v1.schema.json"
            )
            schema = json.loads(schema_path.read_text(encoding="utf-8"))
            Draft202012Validator(schema).validate(manifest)

    def test_rejects_unreasonable_message_count(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                create_synthetic_mcap(Path(directory) / "invalid.mcap", 0)


if __name__ == "__main__":
    unittest.main()
