from __future__ import annotations

import signal
import subprocess
import tempfile
import unittest
from pathlib import Path
from typing import Any

from nrc_edge_gateway.ros2_capture import (
    Ros2BagRecorder,
    Ros2CaptureError,
    Ros2RecordOptions,
    build_ros2_record_command,
)
from nrc_edge_gateway.synthetic_fixture import create_synthetic_mcap


class FakeProcess:
    def __init__(self, timeout_first_wait: bool = False) -> None:
        self.pid = 43210
        self.returncode: int | None = None
        self.timeout_first_wait = timeout_first_wait
        self.wait_count = 0
        self.killed = False

    def wait(self, timeout: float | None = None) -> int:
        self.wait_count += 1
        if self.timeout_first_wait and self.wait_count == 1:
            raise subprocess.TimeoutExpired("ros2 bag record", timeout)
        self.returncode = 0
        return 0

    def poll(self) -> int | None:
        return self.returncode

    def kill(self) -> None:
        self.killed = True
        self.returncode = -signal.SIGKILL


class Ros2CaptureTests(unittest.TestCase):
    def test_command_uses_mcap_and_explicit_topic_allowlist(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            qos_path = Path(directory) / "qos.yaml"
            qos_path.write_text("{}", encoding="utf-8")
            options = Ros2RecordOptions(
                topics=("/imu/data", "/scan"),
                output_directory=Path(directory) / "capture",
                max_cache_size_bytes=1024 * 1024,
                qos_profile_overrides_path=qos_path,
                use_sim_time=True,
            )

            command = build_ros2_record_command(options)

            self.assertEqual(command[:4], ["ros2", "bag", "record", "--storage"])
            self.assertIn("mcap", command)
            self.assertIn("--topics", command)
            topic_index = command.index("--topics")
            self.assertEqual(command[topic_index + 1 : topic_index + 3], ["/imu/data", "/scan"])
            self.assertIn("--max-cache-size", command)
            self.assertIn("--qos-profile-overrides-path", command)
            self.assertIn("--use-sim-time", command)
            self.assertNotIn("--all", command)

    def test_record_returns_mcap_files_from_rosbag_directory(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "capture"
            process = FakeProcess()
            captured: dict[str, Any] = {}

            def fake_popen(command: list[str], start_new_session: bool) -> FakeProcess:
                captured["command"] = command
                captured["start_new_session"] = start_new_session
                output.mkdir()
                create_synthetic_mcap(output / "capture_0.mcap", message_count=2)
                (output / "metadata.yaml").write_text("storage_identifier: mcap\n")
                return process

            recorder = Ros2BagRecorder(
                popen_factory=fake_popen,
                executable_resolver=lambda _: "/usr/bin/ros2",
            )
            files = recorder.record(
                Ros2RecordOptions(
                    topics=("/imu/data",),
                    output_directory=output,
                )
            )

            self.assertEqual([path.name for path in files], ["capture_0.mcap"])
            self.assertTrue(captured["start_new_session"])
            self.assertEqual(captured["command"][0], "/usr/bin/ros2")

    def test_duration_expiry_sends_sigint_for_graceful_stop(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "capture"
            process = FakeProcess(timeout_first_wait=True)
            signals: list[tuple[int, int]] = []

            def fake_popen(command: list[str], start_new_session: bool) -> FakeProcess:
                output.mkdir()
                create_synthetic_mcap(output / "capture_0.mcap", message_count=1)
                return process

            recorder = Ros2BagRecorder(
                popen_factory=fake_popen,
                executable_resolver=lambda _: "/usr/bin/ros2",
                kill_process_group=lambda pid, sig: signals.append((pid, sig)),
            )
            recorder.record(
                Ros2RecordOptions(
                    topics=("/imu/data",),
                    output_directory=output,
                ),
                duration_seconds=0.01,
            )

            self.assertEqual(signals, [(process.pid, signal.SIGINT)])

    def test_rejects_relative_and_duplicate_topics(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                build_ros2_record_command(
                    Ros2RecordOptions(
                        topics=("imu/data",),
                        output_directory=Path(directory) / "capture",
                    )
                )
            with self.assertRaises(ValueError):
                build_ros2_record_command(
                    Ros2RecordOptions(
                        topics=("/imu/data", "/imu/data"),
                        output_directory=Path(directory) / "capture",
                    )
                )

    def test_rejects_existing_output_directory(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "capture"
            output.mkdir()
            recorder = Ros2BagRecorder(
                popen_factory=lambda *args, **kwargs: FakeProcess(),
                executable_resolver=lambda _: "/usr/bin/ros2",
            )
            with self.assertRaises(ValueError):
                recorder.record(
                    Ros2RecordOptions(
                        topics=("/imu/data",),
                        output_directory=output,
                    )
                )

    def test_missing_mcap_plugin_output_is_not_reported_as_success(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "capture"

            def fake_popen(command: list[str], start_new_session: bool) -> FakeProcess:
                output.mkdir()
                return FakeProcess()

            recorder = Ros2BagRecorder(
                popen_factory=fake_popen,
                executable_resolver=lambda _: "/usr/bin/ros2",
            )
            with self.assertRaises(Ros2CaptureError):
                recorder.record(
                    Ros2RecordOptions(
                        topics=("/imu/data",),
                        output_directory=output,
                    )
                )


if __name__ == "__main__":
    unittest.main()
