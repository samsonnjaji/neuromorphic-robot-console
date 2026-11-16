"""Read-only ROS 2 bag capture through the official rosbag2 MCAP storage plugin."""

from __future__ import annotations

import os
import re
import shutil
import signal
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Sequence


DEFAULT_MAX_CACHE_SIZE_BYTES = 64 * 1024 * 1024
MAX_CACHE_SIZE_BYTES = 4_294_967_295
_TOPIC_PATTERN = re.compile(r"/[A-Za-z_][A-Za-z0-9_]*(?:/[A-Za-z_][A-Za-z0-9_]*)*")


class Ros2CaptureError(RuntimeError):
    """Raised when the ROS 2 recorder cannot produce a valid MCAP recording."""


@dataclass(frozen=True)
class Ros2RecordOptions:
    topics: tuple[str, ...]
    output_directory: Path
    max_cache_size_bytes: int = DEFAULT_MAX_CACHE_SIZE_BYTES
    qos_profile_overrides_path: Path | None = None
    use_sim_time: bool = False
    ros2_executable: str = "ros2"


def validate_ros2_record_options(options: Ros2RecordOptions) -> None:
    if not options.topics:
        raise ValueError("at least one explicit ROS 2 topic is required")
    if len(set(options.topics)) != len(options.topics):
        raise ValueError("ROS 2 topic allowlist must not contain duplicates")
    for topic in options.topics:
        if not _TOPIC_PATTERN.fullmatch(topic):
            raise ValueError(
                f"invalid ROS 2 topic {topic!r}; use an absolute topic name such as /imu/data"
            )
    if not 0 <= options.max_cache_size_bytes <= MAX_CACHE_SIZE_BYTES:
        raise ValueError(
            f"max_cache_size_bytes must be between 0 and {MAX_CACHE_SIZE_BYTES}"
        )
    if not options.ros2_executable.strip():
        raise ValueError("ros2_executable must not be blank")
    if options.qos_profile_overrides_path is not None:
        qos_path = Path(options.qos_profile_overrides_path).expanduser()
        if not qos_path.is_file():
            raise ValueError(f"QoS overrides file does not exist: {qos_path}")


def build_ros2_record_command(options: Ros2RecordOptions) -> list[str]:
    """Build an argv list; no shell is used and topics remain an explicit allowlist."""
    validate_ros2_record_options(options)
    output_directory = Path(options.output_directory).expanduser().resolve()
    command = [
        options.ros2_executable,
        "bag",
        "record",
        "--storage",
        "mcap",
        "--output",
        str(output_directory),
        "--topics",
        *options.topics,
        "--max-cache-size",
        str(options.max_cache_size_bytes),
        "--disable-keyboard-controls",
    ]
    if options.qos_profile_overrides_path is not None:
        command.extend(
            [
                "--qos-profile-overrides-path",
                str(Path(options.qos_profile_overrides_path).expanduser().resolve()),
            ]
        )
    if options.use_sim_time:
        command.append("--use-sim-time")
    return command


class Ros2BagRecorder:
    """Run ros2 bag record in a separate process group and stop it gracefully."""

    def __init__(
        self,
        popen_factory: Callable[..., Any] | None = None,
        executable_resolver: Callable[[str], str | None] | None = None,
        kill_process_group: Callable[[int, int], None] | None = None,
    ) -> None:
        self._popen = popen_factory or subprocess.Popen
        self._which = executable_resolver or shutil.which
        self._killpg = kill_process_group or os.killpg

    def record(
        self,
        options: Ros2RecordOptions,
        duration_seconds: float | None = None,
    ) -> list[Path]:
        if os.name != "posix":
            raise Ros2CaptureError(
                "ROS 2 bag capture currently requires Linux or WSL; run this command inside Ubuntu WSL"
            )
        validate_ros2_record_options(options)
        if duration_seconds is not None and duration_seconds <= 0:
            raise ValueError("duration_seconds must be greater than zero")

        executable = self._which(options.ros2_executable)
        if executable is None:
            raise Ros2CaptureError(
                f"{options.ros2_executable!r} was not found; source the ROS 2 setup file and install the MCAP storage plugin"
            )

        output_directory = Path(options.output_directory).expanduser().resolve()
        if output_directory.exists():
            raise ValueError(
                f"output directory already exists; choose a new path: {output_directory}"
            )
        output_directory.parent.mkdir(parents=True, exist_ok=True)

        command = build_ros2_record_command(options)
        command[0] = executable
        process = self._popen(command, start_new_session=True)

        try:
            if duration_seconds is None:
                return_code = process.wait()
            else:
                try:
                    return_code = process.wait(timeout=duration_seconds)
                except subprocess.TimeoutExpired:
                    return_code = self._stop_process(process)
        except KeyboardInterrupt:
            return_code = self._stop_process(process)

        accepted_codes = {0, 130, -signal.SIGINT}
        if return_code not in accepted_codes:
            raise Ros2CaptureError(
                f"ros2 bag record exited with status {return_code}; inspect the ROS 2 recorder output"
            )

        mcap_files = sorted(output_directory.rglob("*.mcap"))
        if not mcap_files:
            raise Ros2CaptureError(
                "ROS 2 recorder stopped without producing an MCAP file; verify the topic allowlist, publishers and rosbag2 MCAP plugin"
            )
        return mcap_files

    def _stop_process(self, process: Any) -> int:
        if process.poll() is None:
            try:
                self._killpg(process.pid, signal.SIGINT)
            except ProcessLookupError:
                pass
        try:
            return int(process.wait(timeout=30))
        except subprocess.TimeoutExpired:
            try:
                self._killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                return int(process.wait(timeout=10))
            except subprocess.TimeoutExpired:
                process.kill()
                return int(process.wait(timeout=10))
