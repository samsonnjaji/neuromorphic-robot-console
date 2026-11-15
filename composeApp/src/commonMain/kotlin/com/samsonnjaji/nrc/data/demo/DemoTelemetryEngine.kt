package com.samsonnjaji.nrc.data.demo

import com.samsonnjaji.nrc.core.safety.SafetyEnvelope
import com.samsonnjaji.nrc.data.telemetry.NrcTelemetrySource
import com.samsonnjaji.nrc.domain.mission.MissionConsoleState
import com.samsonnjaji.nrc.domain.mission.MissionRecorder
import com.samsonnjaji.nrc.domain.model.Detection
import com.samsonnjaji.nrc.domain.model.EventSample
import com.samsonnjaji.nrc.domain.model.ImuReading
import com.samsonnjaji.nrc.domain.model.LidarPoint
import com.samsonnjaji.nrc.domain.model.RobotTelemetry
import com.samsonnjaji.nrc.domain.model.SnnLayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Hardware-free 10 Hz telemetry source for UI development and judge demonstrations. */
class DemoTelemetryEngine(
    private val safety: SafetyEnvelope = SafetyEnvelope(),
    private val recorder: MissionRecorder = MissionRecorder()
) : NrcTelemetrySource {
    private val _telemetry = MutableStateFlow(initialSnapshot())
    override val telemetry: StateFlow<RobotTelemetry> = _telemetry.asStateFlow()

    private val _isConnected = MutableStateFlow(true)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    override val sourceLabel: String = "SIMULATOR"

    private val _missionState = MutableStateFlow(MissionConsoleState())
    val missionState: StateFlow<MissionConsoleState> = _missionState.asStateFlow()

    private var simulationJob: Job? = null
    private var replayJob: Job? = null
    private var tick = 0L

    override fun start(scope: CoroutineScope) {
        if (simulationJob?.isActive == true) return
        _isConnected.value = true
        simulationJob = scope.launch {
            while (isActive) {
                delay(100)
                if (_missionState.value.isReplaying) continue

                tick += 1
                val next = nextSnapshot(_telemetry.value, tick)
                _telemetry.value = next

                if (recorder.isRecording) {
                    recorder.append(next)
                    val saved = recorder.latestRecording
                    _missionState.value = _missionState.value.copy(
                        isRecording = recorder.isRecording,
                        recordedFrames = recorder.frameCount,
                        availableFrames = saved?.frameCount ?: _missionState.value.availableFrames,
                        durationMs = saved?.durationMs ?: _missionState.value.durationMs
                    )
                }
            }
        }
    }

    override fun stop() {
        if (recorder.isRecording) stopRecording()
        stopReplay()
        simulationJob?.cancel()
        simulationJob = null
        _isConnected.value = false
    }

    fun setTargetSpeed(requestedMps: Float) {
        val current = _telemetry.value
        _telemetry.value = current.copy(
            targetSpeedMps = safety.limitLinearSpeed(requestedMps, current.emergencyStop)
        )
    }

    fun triggerEmergencyStop() {
        _telemetry.value = _telemetry.value.copy(
            emergencyStop = true,
            targetSpeedMps = 0f,
            linearVelocityMps = 0f
        )
    }

    fun resetEmergencyStop() {
        _telemetry.value = _telemetry.value.copy(emergencyStop = false)
    }

    fun toggleMission() {
        _telemetry.value = _telemetry.value.copy(
            missionRunning = !_telemetry.value.missionRunning
        )
    }

    fun startRecording() {
        stopReplay()
        recorder.start()
        _missionState.value = MissionConsoleState(isRecording = true)
    }

    fun stopRecording() {
        val recording = recorder.stop()
        _missionState.value = _missionState.value.copy(
            isRecording = false,
            recordedFrames = recording?.frameCount ?: recorder.frameCount,
            availableFrames = recording?.frameCount ?: _missionState.value.availableFrames,
            durationMs = recording?.durationMs ?: _missionState.value.durationMs
        )
    }

    fun replayMission(scope: CoroutineScope) {
        val recording = recorder.latestRecording ?: return
        if (recording.frames.isEmpty()) return

        replayJob?.cancel()
        _missionState.value = _missionState.value.copy(isRecording = false, isReplaying = true)
        replayJob = scope.launch {
            try {
                recording.frames.forEachIndexed { index, frame ->
                    _telemetry.value = frame.telemetry
                    val next = recording.frames.getOrNull(index + 1)
                    if (next != null) {
                        delay((next.offsetMs - frame.offsetMs).coerceIn(1L, 1_000L))
                    }
                }
            } finally {
                _missionState.value = _missionState.value.copy(isReplaying = false)
            }
        }
    }

    fun stopReplay() {
        replayJob?.cancel()
        replayJob = null
        _missionState.value = _missionState.value.copy(isReplaying = false)
    }

    private fun nextSnapshot(previous: RobotTelemetry, step: Long): RobotTelemetry {
        val phase = step / 10f
        val target = safety.limitLinearSpeed(previous.targetSpeedMps, previous.emergencyStop)
        val velocity = if (previous.missionRunning && !previous.emergencyStop) target * 0.92f else 0f

        val lidar = (0 until 72).map { index ->
            val angle = index / 72f * (2f * PI.toFloat())
            val distance = 1.7f + 0.65f * sin(angle * 2.2f + phase * 0.35f) +
                0.15f * cos(angle * 5f)
            LidarPoint(angle, distance.coerceAtLeast(0.25f))
        }
        val events = (0 until 90).map { index ->
            val x = ((index * 37 + step * 11) % 640) / 640f
            val y = ((index * 19 + step * 7) % 480) / 480f
            EventSample(x, y, if ((index + step.toInt()) % 2 == 0) 1 else -1)
        }
        val layers = listOf(
            SnnLayerActivity("Event encoder", 24 + (8 * sin(phase * 0.7f)).toInt(), 128,
                1_800f + 300f * sin(phase), -63f + 2f * sin(phase)),
            SnnLayerActivity("ALIF hidden layer", 38 + (12 * cos(phase * 0.6f)).toInt(), 256,
                3_200f + 650f * cos(phase * 0.6f), -59f + 2.5f * cos(phase * 0.6f)),
            SnnLayerActivity("Decision layer", 6 + (3 * sin(phase * 0.9f)).toInt(), 32,
                520f + 140f * sin(phase * 0.9f), -57f + 1.5f * sin(phase * 0.9f))
        )

        return previous.copy(
            sequence = step,
            timestampMs = step * 100,
            batteryPercent = (100 - step / 900).coerceAtLeast(12).toInt(),
            linearVelocityMps = velocity,
            angularVelocityRadS = 0.12f * sin(phase * 0.4f),
            xMeters = previous.xMeters + velocity * 0.1f * cos(previous.headingRadians),
            yMeters = previous.yMeters + velocity * 0.1f * sin(previous.headingRadians),
            headingRadians = previous.headingRadians + 0.012f * sin(phase * 0.4f),
            cpuLoadPercent = (34 + 12 * sin(phase * 0.3f)).toInt().coerceIn(5, 95),
            inferenceLatencyMs = 8.5f + 1.8f * (1f + sin(phase * 0.7f)),
            eventRateHz = (42_000 + 9_000 * sin(phase * 0.6f)).toInt(),
            lidar = lidar,
            events = events,
            snnLayers = layers,
            imu = ImuReading(
                accelerationX = 0.08f * sin(phase),
                accelerationY = 0.06f * cos(phase * 0.8f),
                accelerationZ = 9.81f + 0.04f * sin(phase * 0.5f),
                angularVelocityZ = 0.12f * sin(phase * 0.4f)
            ),
            detections = listOf(
                Detection("Pedestrian", 0.94f, -18f + 4f * sin(phase)),
                Detection("Obstacle", 0.87f, 26f + 3f * cos(phase))
            )
        )
    }

    private fun initialSnapshot() = RobotTelemetry(
        lidar = (0 until 72).map { index ->
            val angle = index / 72f * (2f * PI.toFloat())
            LidarPoint(angle, 1.8f + 0.4f * sin(angle * 2f))
        },
        events = (0 until 90).map { index ->
            EventSample((index * 37 % 640) / 640f, (index * 19 % 480) / 480f,
                if (index % 2 == 0) 1 else -1)
        },
        detections = listOf(
            Detection("Pedestrian", 0.94f, -18f),
            Detection("Obstacle", 0.87f, 26f)
        ),
        snnLayers = listOf(
            SnnLayerActivity("Event encoder", 24, 128, 1_800f, -63f),
            SnnLayerActivity("ALIF hidden layer", 38, 256, 3_200f, -59f),
            SnnLayerActivity("Decision layer", 6, 32, 520f, -57f)
        )
    )
}
