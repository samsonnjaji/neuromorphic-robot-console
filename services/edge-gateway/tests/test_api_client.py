from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from typing import Any

from nrc_edge_gateway.api_client import NrcApiClient


class FakeUploadClient(NrcApiClient):
    def __init__(self, mode: str) -> None:
        super().__init__("http://127.0.0.1:8080")
        self.mode = mode
        self.calls: list[tuple[str, dict[str, Any]]] = []
        self.uploads: list[tuple[int, int, bytes]] = []
        self.completed_parts: list[dict[str, Any]] | None = None

    def post_json(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        self.calls.append((path, payload))
        if path.endswith("/recordings/recording-1/artifacts"):
            if self.mode == "SINGLE_PUT":
                return {
                    "artifact": {"id": "artifact-1", "uploadMode": "SINGLE_PUT"},
                    "uploadUrl": "http://object-store/single",
                    "requiredHeaders": {"Content-Type": "application/octet-stream"},
                }
            return {
                "artifact": {
                    "id": "artifact-1",
                    "uploadMode": "MULTIPART",
                    "partSizeBytes": 4,
                    "partCount": 3,
                }
            }
        if "/parts/" in path:
            return {
                "uploadUrl": "http://object-store/part",
                "requiredHeaders": {"Content-Type": "application/octet-stream"},
            }
        if path.endswith("/complete"):
            self.completed_parts = payload["parts"]
            return {"id": "artifact-1", "status": "VERIFIED"}
        raise AssertionError(f"Unexpected API request: {path}")

    def _put_stream(
        self,
        upload_url: str,
        file_path: Path,
        offset: int,
        length: int,
        headers: dict[str, str],
    ) -> str:
        with file_path.open("rb") as stream:
            stream.seek(offset)
            content = stream.read(length)
        self.uploads.append((offset, length, content))
        return f'"etag-{offset}"'


class ArtifactUploadTests(unittest.TestCase):
    def test_single_put_upload_is_completed_and_verified(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "small.mcap"
            path.write_bytes(b"small-mcap")

            client = FakeUploadClient("SINGLE_PUT")
            result = client.upload_artifact("recording-1", path)

            self.assertEqual(result["status"], "VERIFIED")
            self.assertEqual(client.uploads, [(0, 10, b"small-mcap")])
            self.assertEqual(client.completed_parts, [])

    def test_multipart_upload_uses_bounded_ordered_parts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "large.mcap"
            path.write_bytes(b"0123456789")

            client = FakeUploadClient("MULTIPART")
            result = client.upload_artifact("recording-1", path)

            self.assertEqual(result["status"], "VERIFIED")
            self.assertEqual(
                client.uploads,
                [
                    (0, 4, b"0123"),
                    (4, 4, b"4567"),
                    (8, 2, b"89"),
                ],
            )
            self.assertEqual(
                client.completed_parts,
                [
                    {"partNumber": 1, "etag": '"etag-0"'},
                    {"partNumber": 2, "etag": '"etag-4"'},
                    {"partNumber": 3, "etag": '"etag-8"'},
                ],
            )


if __name__ == "__main__":
    unittest.main()
