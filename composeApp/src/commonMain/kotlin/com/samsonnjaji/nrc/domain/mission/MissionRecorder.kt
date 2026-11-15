package com.samsonnjaji.nrc.domain.mission

import com.samsonnjaji.nrc.domain.model.RobotTelemetry

data class RecordedTelemetryFrame(
    val offsetMs: Long,
    val telemetry: RobotTelemetry
)

data class MissionRecording(
    val frames: List<RecordedTelemetryFrame>
) {
    val frameCount: Int get() = frames.size
    val durationMs: Long get() = frames.lastOrNull()?.offsetMs ?: 0L
}

data class MissionConsoleState(
    val isRecording: Boolean = false,
    val recordedFrames: Int = 0,
    val availableFrames: Int = 0,
    val durationMs: Long = 0L,
    val isReplaying: Boolean = false
)

/** Bounded in-memory capture; persisted recording formats are a later milestone. */
class MissionRecorder(
    private val maxFrames: Int = 3_600
) {
    private val frames = mutableListOf<RecordedTelemetryFrame>()
    private var firstTimestampMs: Long? = null

    var isRecording: Boolean = false
        private set

    var latestRecording: MissionRecording? = null
        private set

    val frameCount: Int get() = frames.size

    init {
        require(maxFrames > 0) { "Maximum frame count must be positive." }
    }

    fun start() {
        frames.clear()
        firstTimestampMs = null
        latestRecording = null
        isRecording = true
    }

    fun append(telemetry: RobotTelemetry): Boolean {
        if (!isRecording || frames.size >= maxFrames || telemetry.timestampMs < 0L) {
            isRecording = false
            return false
        }

        val first = firstTimestampMs ?: telemetry.timestampMs.also { firstTimestampMs = it }
        val offset = telemetry.timestampMs - first
        if (offset < 0L) return false

        val previousOffset = frames.lastOrNull()?.offsetMs
        if (previousOffset != null && offset < previousOffset) return false

        frames += RecordedTelemetryFrame(offset, telemetry)
        if (frames.size >= maxFrames) {
            isRecording = false
            latestRecording = MissionRecording(frames.toList())
        }
        return true
    }

    fun stop(): MissionRecording? {
        isRecording = false
        if (frames.isEmpty()) return null
        return MissionRecording(frames.toList()).also { latestRecording = it }
    }
}
