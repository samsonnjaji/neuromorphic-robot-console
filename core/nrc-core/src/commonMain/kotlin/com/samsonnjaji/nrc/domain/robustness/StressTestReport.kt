package com.samsonnjaji.nrc.domain.robustness

import com.samsonnjaji.nrc.domain.model.RobotTelemetry
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class StressTestReport(
    val manifest: ExperimentManifest,
    val scenario: EnvironmentScenario,
    val stressedTelemetry: RobotTelemetry,
    val inputLidarPoints: Int,
    val retainedLidarPoints: Int,
    val inputEventSamples: Int,
    val retainedEventSamples: Int,
    val addedNoiseEvents: Int,
    val meanConfidenceBefore: Float?,
    val meanConfidenceAfter: Float?,
    val latencyBeforeMs: Float,
    val latencyAfterMs: Float
) {
    val lidarRetentionPercent: Int?
        get() = retentionPercent(retainedLidarPoints, inputLidarPoints)

    val eventRetentionPercent: Int?
        get() = retentionPercent(retainedEventSamples, inputEventSamples)

    val confidenceRetentionPercent: Int?
        get() = if (meanConfidenceBefore == null || meanConfidenceBefore <= 0f) null
        else ((meanConfidenceAfter ?: 0f) / meanConfidenceBefore * 100f).roundToInt()

    val latencyChangePercent: Int?
        get() = if (latencyBeforeMs <= 0f) null
        else (((latencyAfterMs - latencyBeforeMs) / latencyBeforeMs) * 100f).roundToInt()

    private fun retentionPercent(retained: Int, total: Int): Int? =
        if (total <= 0) null else (retained.toFloat() / total * 100f).roundToInt()
}
