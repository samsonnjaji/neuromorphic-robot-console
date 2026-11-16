"""Small standard-library NRC API client with bounded-memory object uploads."""

from __future__ import annotations

import hashlib
import http.client
import json
import ssl
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any
from urllib.parse import quote, urlsplit


class NrcApiError(RuntimeError):
    """An NRC API or object-storage request failed."""


class NrcApiClient:
    def __init__(self, base_url: str, timeout_seconds: float = 60.0) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds
        parsed = urlsplit(self.base_url)
        if parsed.scheme not in {"http", "https"} or not parsed.netloc:
            raise ValueError("NRC API URL must be an absolute http or https URL")

    def post_json(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path,
            data=body,
            headers={
                "Accept": "application/json",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(
                request, timeout=self.timeout_seconds
            ) as response:
                decoded = response.read().decode("utf-8")
        except urllib.error.HTTPError as failure:
            details = failure.read().decode("utf-8", errors="replace")
            raise NrcApiError(
                f"POST {path} returned HTTP {failure.code}: {details[:1000]}"
            ) from failure
        except (urllib.error.URLError, TimeoutError) as failure:
            raise NrcApiError(f"POST {path} could not reach the NRC API: {failure}") from failure

        try:
            value = json.loads(decoded)
        except json.JSONDecodeError as failure:
            raise NrcApiError(f"POST {path} returned invalid JSON") from failure
        if not isinstance(value, dict):
            raise NrcApiError(f"POST {path} returned an unexpected JSON value")
        return value

    def upload_artifact(
        self,
        recording_id: str,
        file_path: str | Path,
        content_type: str = "application/octet-stream",
        sha256_hex: str | None = None,
    ) -> dict[str, Any]:
        path = Path(file_path)
        size_bytes = path.stat().st_size
        if size_bytes <= 0:
            raise ValueError("Cannot upload an empty artifact")

        artifact_request = {
            "fileName": path.name,
            "contentType": content_type,
            "sizeBytes": size_bytes,
            "sha256Hex": sha256_hex or _sha256(path),
        }
        recording_segment = quote(recording_id, safe="")
        intent = self.post_json(
            f"/api/v1/recordings/{recording_segment}/artifacts",
            artifact_request,
        )
        artifact = intent.get("artifact")
        if not isinstance(artifact, dict) or not artifact.get("id"):
            raise NrcApiError("Artifact upload intent did not contain an artifact ID")

        artifact_id = str(artifact["id"])
        artifact_segment = quote(artifact_id, safe="")
        upload_mode = artifact.get("uploadMode")

        if upload_mode == "SINGLE_PUT":
            upload_url = intent.get("uploadUrl")
            headers = intent.get("requiredHeaders") or {}
            if not upload_url:
                refreshed = self.post_json(
                    f"/api/v1/artifacts/{artifact_segment}/upload-url", {}
                )
                upload_url = refreshed.get("uploadUrl")
                headers = refreshed.get("requiredHeaders") or {}
            if not upload_url:
                raise NrcApiError("Single-part upload URL was not returned by the API")
            self._put_stream(
                str(upload_url), path, offset=0, length=size_bytes, headers=headers
            )
            completed_parts: list[dict[str, Any]] = []
        elif upload_mode == "MULTIPART":
            part_size = int(artifact.get("partSizeBytes") or 0)
            part_count = int(artifact.get("partCount") or 0)
            if part_size <= 0 or part_count <= 0:
                raise NrcApiError("Multipart upload intent has invalid part sizing")

            completed_parts = []
            for part_number in range(1, part_count + 1):
                part_offset = (part_number - 1) * part_size
                part_length = min(part_size, size_bytes - part_offset)
                if part_length <= 0:
                    raise NrcApiError("Multipart upload plan exceeds the declared file size")

                part = self.post_json(
                    f"/api/v1/artifacts/{artifact_segment}/parts/{part_number}/upload-url",
                    {},
                )
                upload_url = part.get("uploadUrl")
                if not upload_url:
                    raise NrcApiError(
                        f"Multipart upload URL was not returned for part {part_number}"
                    )
                etag = self._put_stream(
                    str(upload_url),
                    path,
                    offset=part_offset,
                    length=part_length,
                    headers=part.get("requiredHeaders") or {},
                )
                if not etag:
                    raise NrcApiError(
                        f"Object storage did not return an ETag for part {part_number}"
                    )
                completed_parts.append(
                    {"partNumber": part_number, "etag": etag}
                )

        else:
            raise NrcApiError(f"Unsupported artifact upload mode: {upload_mode!r}")

        completed = self.post_json(
            f"/api/v1/artifacts/{artifact_segment}/complete",
            {"parts": completed_parts},
        )
        if completed.get("status") != "VERIFIED":
            raise NrcApiError(
                f"Artifact {artifact_id} did not reach VERIFIED status"
            )
        return completed

    def _put_stream(
        self,
        upload_url: str,
        file_path: Path,
        offset: int,
        length: int,
        headers: dict[str, str],
    ) -> str | None:
        parsed = urlsplit(upload_url)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise NrcApiError("Object storage returned an invalid upload URL")

        request_target = parsed.path or "/"
        if parsed.query:
            request_target += "?" + parsed.query

        if parsed.scheme == "https":
            connection: http.client.HTTPConnection = http.client.HTTPSConnection(
                parsed.hostname,
                parsed.port,
                timeout=self.timeout_seconds,
                context=ssl.create_default_context(),
            )
        else:
            connection = http.client.HTTPConnection(
                parsed.hostname, parsed.port, timeout=self.timeout_seconds
            )

        try:
            connection.putrequest("PUT", request_target, skip_accept_encoding=True)
            connection.putheader("Content-Length", str(length))
            connection.putheader("Connection", "close")
            for name, value in headers.items():
                if name.lower() in {
                    "host",
                    "content-length",
                    "connection",
                    "transfer-encoding",
                }:
                    continue
                connection.putheader(name, value)
            connection.endheaders()

            remaining = length
            with file_path.open("rb") as stream:
                stream.seek(offset)
                while remaining:
                    chunk = stream.read(min(1024 * 1024, remaining))
                    if not chunk:
                        raise NrcApiError("Artifact file changed or ended during upload")
                    connection.send(chunk)
                    remaining -= len(chunk)

            response = connection.getresponse()
            response_body = response.read()
            if not 200 <= response.status < 300:
                raise NrcApiError(
                    f"Object storage upload returned HTTP {response.status}: "
                    f"{response_body[:500].decode('utf-8', errors='replace')}"
                )
            return response.getheader("ETag")
        except (OSError, http.client.HTTPException, TimeoutError) as failure:
            if isinstance(failure, NrcApiError):
                raise
            raise NrcApiError("Object storage upload failed") from failure
        finally:
            connection.close()


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()
