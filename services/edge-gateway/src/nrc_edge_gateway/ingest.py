"""Orchestrate MCAP imports through the existing NRC experiment and artifact APIs."""

from __future__ import annotations

import json
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from . import __version__
from .api_client import NrcApiClient
from .mcap_inspector import McapInspector


@dataclass(frozen=True)
class IngestResult:
    experiment_id: str
    run_id: str
    recording_session_id: str
    mcap_artifact_id: str
    manifest_artifact_id: str
    manifest: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return {
            "experimentId": self.experiment_id,
            "runId": self.run_id,
            "recordingSessionId": self.recording_session_id,
            "mcapArtifactId": self.mcap_artifact_id,
            "manifestArtifactId": self.manifest_artifact_id,
            "dataOrigin": "RECORDED",
            "inferenceExecuted": False,
            "messageCount": self.manifest["messageCount"],
            "topicCount": self.manifest["topicCount"],
            "sha256Hex": self.manifest["sha256Hex"],
        }


class NrcMcapIngestor:
    def __init__(
        self,
        api: NrcApiClient,
        inspector: McapInspector | None = None,
    ) -> None:
        self.api = api
        self.inspector = inspector or McapInspector()

    def ingest(
        self,
        file_path: str | Path,
        robot_id: str,
        scenario: str,
        source_id: str | None = None,
        experiment_id: str | None = None,
        experiment_name: str | None = None,
        description: str = "",
        model_identifier: str = "not-evaluated",
        model_version: str = "not-evaluated",
    ) -> IngestResult:
        path = Path(file_path).expanduser()
        inspection = self.inspector.inspect(path)
        manifest = inspection.manifest(robot_id=robot_id, source_id=source_id)

        if scenario not in {"CLEAR", "HEAVY_RAIN", "FOG", "LOW_LIGHT", "DUST_SMOKE"}:
            raise ValueError("scenario must be a supported NRC environment scenario")

        created_experiment_id = experiment_id
        if created_experiment_id is None:
            experiment = self.api.post_json(
                "/api/v1/experiments",
                {
                    "name": experiment_name or f"MCAP import: {path.name}",
                    "description": description
                    or "Read-only MCAP ingestion. No perception inference was executed.",
                    "scenario": scenario,
                    "sourceType": "MCAP_RECORDING",
                    "sourceId": manifest["sourceId"],
                    "modelIdentifier": model_identifier,
                    "modelVersion": model_version,
                },
            )
            created_experiment_id = str(experiment["id"])

        run: dict[str, Any] | None = None
        recording: dict[str, Any] | None = None
        run_started = False
        recording_created = False
        recording_started = False
        recording_finalized = False

        try:
            run = self.api.post_json(
                f"/api/v1/experiments/{created_experiment_id}/runs",
                {
                    "sourceRevision": f"nrc-edge-gateway/{__version__}",
                    "configuration": {
                        "dataOrigin": "RECORDED",
                        "sourceFormat": "MCAP",
                        "robotId": robot_id,
                        "edgeGatewayVersion": __version__,
                        "manifestVersion": manifest["schemaVersion"],
                        "recordingSha256Hex": manifest["sha256Hex"],
                        "messageCount": inspection.message_count,
                        "topicCount": inspection.topic_count,
                        "channelCount": inspection.channel_count,
                    },
                },
            )
            run_id = str(run["id"])
            self.api.post_json(
                f"/api/v1/runs/{run_id}/transition", {"status": "RUNNING"}
            )
            run_started = True

            recording = self.api.post_json(
                f"/api/v1/runs/{run_id}/recordings",
                {
                    "format": "MCAP",
                    "sourceId": manifest["sourceId"],
                    "metadata": manifest,
                },
            )
            recording_id = str(recording["id"])
            recording_created = True
            self.api.post_json(
                f"/api/v1/recordings/{recording_id}/transition",
                {"status": "RECORDING"},
            )
            recording_started = True
            self.api.post_json(
                f"/api/v1/recordings/{recording_id}/transition",
                {
                    "status": "FINALIZED",
                    "sampleCount": inspection.message_count,
                    "metadata": manifest,
                },
            )
            recording_started = False
            recording_finalized = True

            with tempfile.TemporaryDirectory(prefix="nrc-edge-manifest-") as directory:
                manifest_path = Path(directory) / f"{path.name}.manifest.json"
                manifest_path.write_text(
                    json.dumps(manifest, indent=2, sort_keys=True) + "\n",
                    encoding="utf-8",
                )
                mcap_artifact = self.api.upload_artifact(
                    recording_id, path, "application/octet-stream"
                )
                manifest_artifact = self.api.upload_artifact(
                    recording_id, manifest_path, "application/json"
                )

            mcap_artifact_id = str(mcap_artifact["id"])
            manifest_artifact_id = str(manifest_artifact["id"])
            self.api.post_json(
                f"/api/v1/runs/{run_id}/transition",
                {
                    "status": "COMPLETED",
                    "resultSummary": {
                        "dataOrigin": "RECORDED",
                        "inferenceExecuted": False,
                        "recordingSessionId": recording_id,
                        "messageCount": inspection.message_count,
                        "topicCount": inspection.topic_count,
                        "artifactIds": [mcap_artifact_id, manifest_artifact_id],
                        "sha256Hex": inspection.sha256_hex,
                    },
                },
            )

            return IngestResult(
                experiment_id=created_experiment_id,
                run_id=run_id,
                recording_session_id=recording_id,
                mcap_artifact_id=mcap_artifact_id,
                manifest_artifact_id=manifest_artifact_id,
                manifest=manifest,
            )
        except Exception as failure:
            reason = str(failure).strip() or failure.__class__.__name__
            reason = reason[:512]
            if recording is not None and recording_created and not recording_finalized:
                recording_id = str(recording.get("id", ""))
                if recording_id:
                    recording_status = "FAILED" if recording_started else "CANCELLED"
                    _best_effort(
                        self.api,
                        f"/api/v1/recordings/{recording_id}/transition",
                        {"status": recording_status, "failureReason": reason},
                    )
            if run is not None:
                run_id = str(run.get("id", ""))
                if run_id:
                    run_status = "FAILED" if run_started else "CANCELLED"
                    _best_effort(
                        self.api,
                        f"/api/v1/runs/{run_id}/transition",
                        {"status": run_status, "failureReason": reason},
                    )
            raise


def _best_effort(
    api: NrcApiClient, path: str, payload: dict[str, Any]
) -> None:
    try:
        api.post_json(path, payload)
    except Exception:
        # Preserve the original failure; lifecycle repair can be inspected in the API.
        pass
