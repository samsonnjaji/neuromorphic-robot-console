from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from typing import Any

from mcap.writer import Writer

from nrc_edge_gateway.ingest import NrcMcapIngestor


def write_fixture(path: Path) -> None:
    with path.open("wb") as output:
        writer = Writer(output)
        writer.start(profile="ros2", library="ingest-test")
        channel_id = writer.register_channel(
            topic="/robot/imu", message_encoding="cdr"
        )
        writer.add_message(
            channel_id,
            log_time=1_700_000_000_000_000_100,
            publish_time=1_700_000_000_000_000_090,
            sequence=1,
            data=b"imu-one",
        )
        writer.add_message(
            channel_id,
            log_time=1_700_000_000_000_000_200,
            publish_time=1_700_000_000_000_000_190,
            sequence=2,
            data=b"imu-two",
        )
        writer.finish()


class FakeApi:
    def __init__(self) -> None:
        self.requests: list[tuple[str, dict[str, Any]]] = []
        self.uploads: list[tuple[str, str, bytes, str]] = []

    def post_json(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        self.requests.append((path, payload))
        if path == "/api/v1/experiments":
            return {"id": "experiment-1"}
        if path.endswith("/runs"):
            return {"id": "run-1"}
        if path == "/api/v1/runs/run-1/transition":
            return {"id": "run-1", "status": payload["status"]}
        if path == "/api/v1/runs/run-1/recordings":
            return {"id": "recording-1"}
        if path == "/api/v1/recordings/recording-1/transition":
            return {"id": "recording-1", "status": payload["status"]}
        raise AssertionError(f"Unexpected API request: {path}")

    def upload_artifact(
        self, recording_id: str, file_path: str | Path, content_type: str
    ) -> dict[str, Any]:
        path = Path(file_path)
        self.uploads.append(
            (recording_id, path.name, path.read_bytes(), content_type)
        )
        return {"id": f"artifact-{len(self.uploads)}", "status": "VERIFIED"}


class IngestWorkflowTests(unittest.TestCase):
    def test_registers_recording_uploads_artifacts_and_completes_run(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "lab-run.mcap"
            write_fixture(path)
            api = FakeApi()

            result = NrcMcapIngestor(api).ingest(
                path,
                robot_id="lab-robot-01",
                source_id="lab-source-01",
                scenario="HEAVY_RAIN",
            )

            self.assertEqual(result.experiment_id, "experiment-1")
            self.assertEqual(result.run_id, "run-1")
            self.assertEqual(result.recording_session_id, "recording-1")
            self.assertEqual(result.manifest["dataOrigin"], "RECORDED")
            self.assertEqual(result.manifest["messageCount"], 2)
            self.assertEqual(len(api.uploads), 2)
            self.assertEqual(api.uploads[0][1], "lab-run.mcap")
            self.assertEqual(api.uploads[0][3], "application/octet-stream")
            self.assertTrue(api.uploads[1][1].endswith(".manifest.json"))
            self.assertEqual(api.uploads[1][3], "application/json")

            recording_create = next(
                payload
                for path_value, payload in api.requests
                if path_value == "/api/v1/runs/run-1/recordings"
            )
            self.assertEqual(recording_create["format"], "MCAP")
            self.assertEqual(
                recording_create["metadata"]["timestamps"]["unit"],
                "nanoseconds",
            )

            final_run_transition = [
                payload
                for path_value, payload in api.requests
                if path_value == "/api/v1/runs/run-1/transition"
            ][-1]
            self.assertEqual(final_run_transition["status"], "COMPLETED")
            self.assertFalse(final_run_transition["resultSummary"]["inferenceExecuted"])

    def test_invalid_file_fails_before_creating_api_records(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.mcap"
            path.write_bytes(b"not a recording")
            api = FakeApi()

            with self.assertRaises(ValueError):
                NrcMcapIngestor(api).ingest(
                    path, robot_id="robot-01", scenario="CLEAR"
                )

            self.assertEqual(api.requests, [])
            self.assertEqual(api.uploads, [])


if __name__ == "__main__":
    unittest.main()
